/**
 * JBoss, Home of Professional Open Source.
 * Copyright 2014 Red Hat, Inc., and individual contributors
 * as indicated by the @author tags.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jboss.pnc.termdbuilddriver;

import org.jboss.pnc.common.Configuration;
import org.jboss.pnc.common.json.ConfigurationParseException;
import org.jboss.pnc.common.json.moduleconfig.SystemConfig;
import org.jboss.pnc.common.json.moduleprovider.PncConfigProvider;
import org.jboss.pnc.model.BuildType;
import org.jboss.pnc.spi.builddriver.BuildDriver;
import org.jboss.pnc.spi.builddriver.BuildDriverResult;
import org.jboss.pnc.spi.builddriver.BuildDriverStatus;
import org.jboss.pnc.spi.builddriver.CompletedBuild;
import org.jboss.pnc.spi.builddriver.RunningBuild;
import org.jboss.pnc.spi.builddriver.exception.BuildDriverException;
import org.jboss.pnc.spi.environment.RunningEnvironment;
import org.jboss.pnc.spi.executor.BuildExecutionSession;
import org.jboss.pnc.termdbuilddriver.commands.TermdCommandBatchExecutionResult;
import org.jboss.pnc.termdbuilddriver.commands.TermdCommandInvoker;
import org.jboss.pnc.termdbuilddriver.transfer.TermdFileTranser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;

import java.net.URI;
import java.nio.file.Paths;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;

@ApplicationScoped
public class TermdBuildDriver implements BuildDriver { //TODO rename class

    public static final String DRIVER_ID = "termd-build-driver";

    private static final Logger logger = LoggerFactory.getLogger(TermdBuildDriver.class);

    //connect to build agent on internal or on public address
    private boolean useInternalNetwork = true; //TODO configurable

    private ExecutorService executor;

    @Deprecated
    public TermdBuildDriver() {
    }
    
    @Inject
    public TermdBuildDriver(Configuration configuration) {
        int executorThreadPoolSize = 12;
        try {
            String executorThreadPoolSizeStr = configuration.getModuleConfig(new PncConfigProvider<>(SystemConfig.class))
                    .getBuilderThreadPoolSize();
            if (executorThreadPoolSizeStr != null) {
                executorThreadPoolSize = Integer.parseInt(executorThreadPoolSizeStr);
            }
        } catch (ConfigurationParseException e) {
            logger.warn("Unable parse config. Using defaults.");
        }
        executor = Executors.newFixedThreadPool(executorThreadPoolSize);
    }

    @Override
    public String getDriverId() {
        return DRIVER_ID;
    }

    @Override
    public boolean canBuild(BuildType buildType) {
        return BuildType.JAVA.equals(buildType);
    }
    
    @Override
    public RunningBuild startProjectBuild(BuildExecutionSession buildExecutionSession,
            final RunningEnvironment runningEnvironment)
            throws BuildDriverException {

        logger.info("[{}] Starting build for Build Execution Session {}", runningEnvironment.getId(), buildExecutionSession.getId());

        TermdRunningBuild termdRunningBuild = new TermdRunningBuild(runningEnvironment, buildExecutionSession.getBuildExecutionConfiguration());

        
        addScriptDebugOption(termdRunningBuild)
                .thenComposeAsync(returnedBuildScript -> changeToWorkingDirectory(termdRunningBuild, returnedBuildScript), executor)
                .thenComposeAsync(returnedBuildScript -> checkoutSources(termdRunningBuild, returnedBuildScript), executor)
                .thenComposeAsync(returnedBuildScript -> build(termdRunningBuild, returnedBuildScript), executor)
                .thenComposeAsync(returnedBuildScript -> uploadScript(termdRunningBuild, returnedBuildScript), executor)
                .thenComposeAsync(scriptPath -> invokeRemoteScript(termdRunningBuild, scriptPath, buildExecutionSession), executor)
                .handle((results, exception) -> updateStatus(termdRunningBuild, results, exception));

        return termdRunningBuild;
    }

    protected CompletableFuture<StringBuilder> addScriptDebugOption(TermdRunningBuild termdRunningBuild) {
        return CompletableFuture.supplyAsync(() -> {
            logger.debug("[{}] Adding debug option", termdRunningBuild.getRunningEnvironment().getId());

            StringBuilder commandAppender = new StringBuilder();
            String debugOption = "set -x";
            commandAppender.append(debugOption).append("\n");
            return commandAppender;
        });
    }

    protected CompletableFuture<StringBuilder> changeToWorkingDirectory(TermdRunningBuild termdRunningBuild, StringBuilder commandAppender) {
        return CompletableFuture.supplyAsync(() -> {
            logger.debug("[{}] Changing current directory", termdRunningBuild.getRunningEnvironment().getId());

            String cdCommand = "cd " + termdRunningBuild.getRunningEnvironment().getWorkingDirectory().toAbsolutePath().toString();
            commandAppender.append(cdCommand).append("\n");
            return commandAppender;
        });
    }

    protected CompletableFuture<StringBuilder> checkoutSources(TermdRunningBuild termdRunningBuild, StringBuilder commandAppender) {
        return CompletableFuture.supplyAsync(() -> {
            logger.debug("[{}] Checking out sources", termdRunningBuild.getRunningEnvironment().getId());

            String cloneCommand = "git clone " + termdRunningBuild.getScmRepoURL() + " " + termdRunningBuild.getName();
            commandAppender.append(cloneCommand).append("\n");

            String cdCommand = "cd " + termdRunningBuild.getName();
            commandAppender.append(cdCommand).append("\n");

            String resetCommand = "git reset --hard " + termdRunningBuild.getScmRevision();
            commandAppender.append(resetCommand).append("\n");

            return commandAppender;
        });
    }

    protected CompletableFuture<StringBuilder> build(TermdRunningBuild termdRunningBuild, StringBuilder commandAppender) {
        return CompletableFuture.supplyAsync(() -> {
            logger.debug("[{}] Building", termdRunningBuild.getRunningEnvironment().getId());

            String buildCommand = termdRunningBuild.getBuildScript();
            commandAppender.append(buildCommand).append("\n");
            return commandAppender;
        });
    }

    protected CompletableFuture<String> uploadScript(TermdRunningBuild termdRunningBuild, StringBuilder commandAppender) {
        return CompletableFuture.supplyAsync(() -> {
            logger.debug("[{}] Uploading script", termdRunningBuild.getRunningEnvironment().getId());
            logger.debug("[{}] Full script:\n {}", termdRunningBuild.getRunningEnvironment().getId(), commandAppender.toString());

            new TermdFileTranser(URI.create(getBuildAgentUrl(termdRunningBuild)))
                    .uploadScript(commandAppender,
                            Paths.get(termdRunningBuild.getRunningEnvironment().getWorkingDirectory().toAbsolutePath().toString(), "run.sh"));

            return termdRunningBuild.getRunningEnvironment().getWorkingDirectory().toAbsolutePath().toString() + "/run.sh";
        });
    }

    protected CompletableFuture<TermdCommandBatchExecutionResult> invokeRemoteScript(
            TermdRunningBuild termdRunningBuild,
            String scriptPath,
            BuildExecutionSession currentBuildExecution) {
        return CompletableFuture.supplyAsync(() -> {
            logger.debug("[{}] Invoking script from path {}", termdRunningBuild.getRunningEnvironment().getId(), scriptPath);

            TermdCommandInvoker termdCommandInvoker = new TermdCommandInvoker(URI.create(getBuildAgentUrl(termdRunningBuild)), termdRunningBuild.getRunningEnvironment().getWorkingDirectory());
            termdCommandInvoker.startSession();

            termdCommandInvoker.performCommand("sh " + scriptPath).join();

            currentBuildExecution.setLiveLogsUri(Optional.empty());//TODO do we really need this ?
            return termdCommandInvoker.closeSession();
        });
    }

    private String getBuildAgentUrl(TermdRunningBuild termdRunningBuild) {
        if (useInternalNetwork) {
            return termdRunningBuild.getRunningEnvironment().getInternalBuildAgentUrl();
        } else {
            return termdRunningBuild.getRunningEnvironment().getBuildAgentUrl();
        }
    }

    protected TermdRunningBuild updateStatus(TermdRunningBuild termdRunningBuild, TermdCommandBatchExecutionResult commandBatchResult, Throwable throwable) {
        logger.debug("[{}] Command result {}", termdRunningBuild.getRunningEnvironment().getId(), commandBatchResult);

        if(throwable != null) {
            logger.warn("[{}] Exception {}", termdRunningBuild.getRunningEnvironment().getId(), throwable);
            termdRunningBuild.setBuildPromiseError((Exception) throwable);
        } else {
            logger.debug("[{}] No Exceptions.", termdRunningBuild.getRunningEnvironment().getId());
            AtomicReference<String> aggregatedLogs = new AtomicReference<>();
            try {
                aggregatedLogs.set(aggregateLogs(termdRunningBuild, commandBatchResult).get().toString());
            } catch (Exception e) {
                termdRunningBuild.setBuildPromiseError(e);
                return termdRunningBuild;
            }

            termdRunningBuild.setCompletedBuild(new CompletedBuild() {
                @Override
                public BuildDriverResult getBuildResult() throws BuildDriverException {
                    return new BuildDriverResult() {
                        @Override
                        public String getBuildLog() {
                            return aggregatedLogs.get();
                        }

                        @Override
                        public BuildDriverStatus getBuildDriverStatus() {
                            return commandBatchResult.isSuccessful() ? BuildDriverStatus.SUCCESS : BuildDriverStatus.FAILED;
                        }
                    };
                }

                @Override
                public RunningEnvironment getRunningEnvironment() {
                    return termdRunningBuild.getRunningEnvironment();
                }});
        }
        return termdRunningBuild;
    }

    protected CompletableFuture<StringBuffer> aggregateLogs(TermdRunningBuild termdRunningBuild, TermdCommandBatchExecutionResult allInvokedCommands) {
        logger.debug("[{}] Aggregating logs", termdRunningBuild.getRunningEnvironment().getId());

        TermdFileTranser transer = new TermdFileTranser();
        return CompletableFuture.supplyAsync(() -> allInvokedCommands.getCommandResults().stream()
                .map(invocationResult -> invocationResult.getLogsUri())
                .reduce(new StringBuffer(),
                        (stringBuffer, uri) -> transer.downloadFileToStringBuilder(stringBuffer, uri),
                        (builder1, builder2) -> builder1.append(builder2)));
    }


}
