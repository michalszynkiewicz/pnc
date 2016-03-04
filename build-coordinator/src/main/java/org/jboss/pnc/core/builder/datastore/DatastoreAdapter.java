/**
 * JBoss, Home of Professional Open Source.
 * Copyright 2014 Red Hat, Inc., and individual contributors
 * as indicated by the @author tags.
 * <p/>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p/>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p/>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jboss.pnc.core.builder.datastore;

import org.jboss.logging.Logger;
import org.jboss.pnc.core.BuildCoordinationException;
import org.jboss.pnc.core.builder.coordinator.BuildTask;
import org.jboss.pnc.model.BuildConfigSetRecord;
import org.jboss.pnc.model.BuildConfiguration;
import org.jboss.pnc.model.BuildConfigurationAudited;
import org.jboss.pnc.model.BuildRecord;
import org.jboss.pnc.spi.BuildResult;
import org.jboss.pnc.spi.builddriver.BuildDriverResult;
import org.jboss.pnc.spi.builddriver.exception.BuildDriverException;
import org.jboss.pnc.spi.datastore.Datastore;
import org.jboss.pnc.spi.datastore.DatastoreException;
import org.jboss.pnc.spi.executor.BuildExecutionConfiguration;
import org.jboss.pnc.spi.repositorymanager.RepositoryManagerResult;

import javax.inject.Inject;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.time.Instant;
import java.util.Date;
import java.util.Optional;

import static org.jboss.pnc.model.BuildStatus.REJECTED;
import static org.jboss.pnc.model.BuildStatus.SYSTEM_ERROR;

/**
 * Created by <a href="mailto:matejonnet@gmail.com">Matej Lazar</a> on 2014-12-15.
 */
public class DatastoreAdapter {

    private Datastore datastore;

    private static final Logger log = Logger.getLogger(DatastoreAdapter.class);

    // needed for EJB/CDI
    @Deprecated
    public DatastoreAdapter() {
    }

    @Inject
    public DatastoreAdapter(Datastore datastore) {
        this.datastore = datastore;
    }

    public BuildConfigSetRecord saveBuildConfigSetRecord(BuildConfigSetRecord buildConfigSetRecord) throws DatastoreException {
        return datastore.saveBuildConfigSetRecord(buildConfigSetRecord);
    }

    /**
     * Get the latest audited revision for the given build configuration ID
     *
     * @param buildConfigurationId
     * @return The latest revision of the given build configuration
     */
    public BuildConfigurationAudited getLatestBuildConfigurationAudited(Integer buildConfigurationId) {
        return datastore.getLatestBuildConfigurationAudited(buildConfigurationId);
    }

    public void storeResult(BuildTask buildTask, BuildResult buildResult) throws DatastoreException {
        try {
            BuildRecord.Builder buildRecordBuilder = initBuildRecordBuilder(buildTask);
            if (!buildResult.getBuildDriverResult().isPresent()) {
                if (!buildResult.hasFailed()) {
                    storeErroneousResult(buildTask,
                            new BuildCoordinationException("Trying to store success build with incomplete result. Missing BuildDriverResult."));
                    return;
                }
            } else {
                BuildDriverResult buildDriverResult = buildResult.getBuildDriverResult().get();
                buildRecordBuilder.buildLog(buildDriverResult.getBuildLog());
                buildRecordBuilder.status(buildDriverResult.getBuildDriverStatus().toBuildStatus());
            }
            if (!buildResult.getRepositoryManagerResult().isPresent()) {
                if (!buildResult.hasFailed()) {
                    storeErroneousResult(buildTask, new BuildCoordinationException("Trying to store success build with incomplete result. Missing RepositoryManagerResult."));
                    return;
                }
            } else {
                RepositoryManagerResult repositoryManagerResult = buildResult.getRepositoryManagerResult().get();
                buildRecordBuilder.buildContentId(repositoryManagerResult.getBuildContentId());
                buildRecordBuilder.builtArtifacts(repositoryManagerResult.getBuiltArtifacts());
                buildRecordBuilder.dependencies(repositoryManagerResult.getDependencies());
            }


            BuildExecutionConfiguration buildExecutionConfig = buildResult.getBuildExecutionConfiguration().get();
            buildRecordBuilder.scmRepoURL(buildExecutionConfig.getScmRepoURL());
            buildRecordBuilder.scmRevision(buildExecutionConfig.getScmRevision());

            buildRecordBuilder.endTime(Date.from(Instant.now()));

            log.debugf("Storing results of buildTask [%s] to datastore.", buildTask.getId());
            datastore.storeCompletedBuild(buildRecordBuilder, buildTask.getBuildRecordSetIds());
        } catch (Exception e) {
            storeErroneousResult(buildTask, e);
        }
    }

    public void storeErroneousResult(BuildTask buildTask, Throwable e) throws DatastoreException {
        BuildRecord.Builder buildRecordBuilder = initBuildRecordBuilder(buildTask);
        StringWriter stackTraceWriter = new StringWriter();
        PrintWriter stackTracePrinter = new PrintWriter(stackTraceWriter);
        e.printStackTrace(stackTracePrinter);
        buildRecordBuilder.status(SYSTEM_ERROR);

        String errorMessage = "Last build status: " + buildTask.getStatus().toString() + "\n";
        errorMessage += "Caught exception: " + stackTraceWriter.toString();
        buildRecordBuilder.buildLog(errorMessage);
        buildRecordBuilder.endTime(Date.from(Instant.now()));

        log.debugf("Storing ERROR result of %s to datastore. Error: %s", buildTask.getBuildConfigurationAudited().getName() + "\n\n\n Exception: " + errorMessage, e);
        datastore.storeCompletedBuild(buildRecordBuilder, buildTask.getBuildRecordSetIds());
    }

    public void storeRejected(BuildTask buildTask) throws DatastoreException {
        BuildRecord.Builder buildRecordBuilder = initBuildRecordBuilder(buildTask);
        buildRecordBuilder.status(REJECTED);

        buildRecordBuilder.buildLog(buildTask.getStatusDescription());
        buildRecordBuilder.endTime(Date.from(Instant.now()));

        log.debugf("Storing REJECTED build of %s to datastore. Reason: %s", buildTask.getBuildConfigurationAudited().getName(), buildTask.getStatusDescription());
        datastore.storeCompletedBuild(buildRecordBuilder, buildTask.getBuildRecordSetIds());
    }

    /**
     * Initialize a new BuildRecord.Builder based on the data contained in the BuildTask.
     * Note, this must be done inside a transaction because it fetches the BuildRecordSet entities from
     * the database.
     *
     * @return The initialized build record builder
     */
    private BuildRecord.Builder initBuildRecordBuilder(BuildTask buildTask) {
        BuildRecord.Builder builder = BuildRecord.Builder.newBuilder().id(buildTask.getId())
                .buildConfigurationAudited(buildTask.getBuildConfigurationAudited())
                .user(buildTask.getUser())
                .submitTime(buildTask.getSubmitTime())
                .startTime(buildTask.getStartTime())
                .endTime(buildTask.getEndTime());


        builder.latestBuildConfiguration(buildTask.getBuildConfiguration());
        if (buildTask.getBuildConfigSetRecordId() != null) {
            BuildConfigSetRecord buildConfigSetRecord = datastore.getBuildConfigSetRecordById(buildTask.getBuildConfigSetRecordId());
            builder.buildConfigSetRecord(buildConfigSetRecord);
        }

        return builder;
    }

    public Integer getNextBuildRecordId() {
        return datastore.getNextBuildRecordId();
    }

    public boolean hasSuccessfulBuildRecord(BuildConfiguration buildConfiguration) {
        return datastore.hasSuccessfulBuildRecord(buildConfiguration);
    }
}
