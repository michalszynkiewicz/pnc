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
package org.jboss.pnc.bpm.task;

import lombok.ToString;
import org.jboss.pnc.bpm.BpmTask;
import org.jboss.pnc.model.ProductMilestone;
import org.jboss.pnc.rest.restmodel.causeway.BrewPushMilestoneRest;
import org.jboss.pnc.spi.exception.CoreException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

/**
 * @author Michal Szynkiewicz
 */
@ToString(callSuper = true)
public class BpmBrewPushTask extends BpmTask {

    private static final Logger log = LoggerFactory.getLogger(BpmBrewPushTask.class);

    private final ProductMilestone milestone;

    public BpmBrewPushTask(ProductMilestone milestone) {
        this.milestone = milestone;
    }

    @Override
    protected Map<String, Object> getExtendedProcessParameters() throws CoreException {
        log.debug("[{}] Creating extended parameters", milestone.getId());
        Map<String, Object> parameters = super.getExtendedProcessParameters();
        parameters.put("brewPush", createMilestoneRest(milestone));
        log.debug("[{}] Created parameters", parameters);
        return parameters;
    }

    private BrewPushMilestoneRest createMilestoneRest(ProductMilestone milestone) {
        return new BrewPushMilestoneRest(milestone.getId());
    }

    protected Map<String, Object> getProcessParameters() throws CoreException {
        Map<String, Object> params = new HashMap<>();
        params.put("pncBaseUrl", config.getPncBaseUrl());
        return params;
    }

    @Override
    protected String getProcessId() {
        return config.getReleaseProcessId();
    }
}
