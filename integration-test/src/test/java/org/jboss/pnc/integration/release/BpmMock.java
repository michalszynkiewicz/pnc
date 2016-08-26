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
package org.jboss.pnc.integration.release;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.apache.commons.lang3.RandomStringUtils;
import org.jboss.pnc.bpm.BpmManager;
import org.jboss.pnc.rest.restmodel.causeway.BrewPushMilestoneRest;
import org.jboss.pnc.spi.exception.CoreException;
import org.kie.api.definition.process.Process;
import org.kie.api.runtime.KieSession;
import org.kie.api.runtime.process.ProcessInstance;

import javax.annotation.PostConstruct;
import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.inject.Alternative;
import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Author: Michal Szynkiewicz, michal.l.szynkiewicz@gmail.com
 * Date: 8/25/16
 * Time: 3:29 PM
 */
@Alternative
@Path("/bpm-manager-mock")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@ApplicationScoped
public class BpmMock extends BpmManager {

    private final List<Integer> expectedMilestones = new ArrayList<>();
    private final List<Push> pushes = new ArrayList<>();
    private MockKieSession session = new MockKieSession();

    public BpmMock() {
        super(null);
    }

    @Override
    protected KieSession initKieSession() throws CoreException {
        expectedMilestones.clear();
        pushes.clear();
        session.onStartProcess(this::startProcessMock);
        return session;
    }

    private ProcessInstance startProcessMock(String processName, Map params) {
        Integer taskId = (Integer) params.get("taskId");
        BrewPushMilestoneRest milestoneRest = (BrewPushMilestoneRest) params.get("brewPush");
        String callbackId = RandomStringUtils.randomNumeric(12);
        pushes.add(new Push(milestoneRest.getMilestoneId(), taskId, callbackId));
        return new ProcessInstanceMock();
    }

    @GET
    @Path("/expect-milestone/{milestoneId}")
    public Response expectMilestone(@PathParam("milestoneId") int milestoneId) {
        this.expectedMilestones.add(milestoneId);
        return Response.ok().build();
    }

    @GET
    @Path("/pushes-for/{milestoneId}")
    public Response getPushesFor(@PathParam("milestoneId") int milestoneId) {
        List<Push> pushes = this.pushes.stream()
                .filter(p -> p.milestoneId == milestoneId)
                .collect(Collectors.toList());
        return Response.ok(new PushList(pushes)).build();
    }

    @PostConstruct
    public void setUp() {
        System.out.println("JKKKDHD");   // mstodo remove
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class PushList {
        List<Push> pushes;
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class Push {
        private int milestoneId;
        private int taskId;
        private String callbackId;
    }

    public static class ProcessInstanceMock implements ProcessInstance {

        @Override
        public String getProcessId() {
            return null;  // TODO: Customise this generated block
        }

        @Override
        public Process getProcess() {
            return null;  // TODO: Customise this generated block
        }

        @Override
        public long getId() {
            return 0;  // TODO: Customise this generated block
        }

        @Override
        public String getProcessName() {
            return null;  // TODO: Customise this generated block
        }

        @Override
        public int getState() {
            return 0;  // TODO: Customise this generated block
        }

        @Override
        public long getParentProcessInstanceId() {
            return 0;  // TODO: Customise this generated block
        }

        @Override
        public void signalEvent(String s, Object o) {
            // TODO: Customise this generated block
        }

        @Override
        public String[] getEventTypes() {
            return new String[0];  // TODO: Customise this generated block
        }
    }

}
