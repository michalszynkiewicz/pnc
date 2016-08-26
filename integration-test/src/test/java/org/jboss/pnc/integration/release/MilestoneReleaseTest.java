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

import com.jayway.restassured.http.ContentType;
import com.jayway.restassured.response.Response;
import com.jayway.restassured.specification.RequestSpecification;
import org.assertj.core.api.Assertions;
import org.jboss.arquillian.container.test.api.Deployment;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.pnc.AbstractTest;
import org.jboss.pnc.common.util.RandomUtils;
import org.jboss.pnc.integration.deployments.Deployments;
import org.jboss.pnc.integration.matchers.JsonMatcher;
import org.jboss.pnc.integration.release.BpmMock.PushList;
import org.jboss.pnc.integration.template.JsonTemplateBuilder;
import org.jboss.pnc.rest.endpoint.BpmEndpoint;
import org.jboss.pnc.rest.endpoint.ProductMilestoneEndpoint;
import org.jboss.pnc.rest.restmodel.ProductMilestoneRest;
import org.jboss.pnc.rest.restmodel.causeway.BrewPushMilestoneResultRest;
import org.jboss.pnc.rest.restmodel.causeway.BuildImportResultRest;
import org.jboss.pnc.rest.restmodel.causeway.BuildImportStatus;
import org.jboss.pnc.rest.restmodel.causeway.CallbackResultRest;
import org.jboss.pnc.test.category.ContainerTest;
import org.jboss.shrinkwrap.api.asset.StringAsset;
import org.jboss.shrinkwrap.api.spec.EnterpriseArchive;
import org.jboss.shrinkwrap.api.spec.WebArchive;
import org.jboss.shrinkwrap.descriptor.api.Descriptors;
import org.jboss.shrinkwrap.descriptor.api.beans10.BeansDescriptor;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Date;
import java.util.List;

import static com.jayway.restassured.RestAssured.given;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.jboss.pnc.integration.env.IntegrationTestEnv.getHttpPort;
import static org.jboss.pnc.integration.matchers.JsonMatcher.containsJsonAttribute;

/**
 * Author: Michal Szynkiewicz, michal.l.szynkiewicz@gmail.com
 * Date: 8/25/16
 * Time: 3:21 PM
 */

@RunWith(Arquillian.class)
@Category(ContainerTest.class)
public class MilestoneReleaseTest extends AbstractTest {
    public static final String BPM_MANAGER_MOCK = "/pnc-rest/rest/bpm-manager-mock/";
    public static final String BPM_MANAGER_MOCK_EXPECT_MILESTONE = BPM_MANAGER_MOCK + "expect-milestone/%d";
    public static final String BPM_MANAGER_MOCK_PUSHES_FOR = BPM_MANAGER_MOCK + "pushes-for/%d";

    private static final Logger log = LoggerFactory.getLogger(MilestoneReleaseTest.class);

    private int productId;
    private int productVersionId;
    private int milestoneId;
    private String callbackId;
    private int taskId;

    @Deployment(testable = false)
    public static EnterpriseArchive deploy() {

        EnterpriseArchive enterpriseArchive = Deployments.baseEar();

        WebArchive restWar = enterpriseArchive.getAsType(WebArchive.class, REST_WAR_PATH);
        restWar.addClass(ProductMilestoneEndpoint.class);
        restWar.addClass(BpmEndpoint.class);
        restWar.addClass(BpmMock.class);
        restWar.addClass(MockKieSession.class);
        restWar.addAsWebInfResource(new StringAsset(
                Descriptors.create(BeansDescriptor.class).
                        getOrCreateAlternatives().
                        clazz(BpmMock.class.
                                getName()).up().exportAsString()), "beans.xml");
        log.info(enterpriseArchive.toString(true));
        return enterpriseArchive;
    }

    public void createAndReleaseMilestone() throws IOException {
        prepareMilestone();
        prepareBpmMock();

        triggerMilestoneRelease(); // mstodo not all put operations should do it!!!

        assertLogContains("Brew push task started");
        assertBpmMockGotCalled();
    }

    @Test
    public void shouldProcessBpmCallback() throws IOException {
        createAndReleaseMilestone();
        BrewPushMilestoneResultRest result = new BrewPushMilestoneResultRest();
        BuildImportResultRest importResult = new BuildImportResultRest();
        importResult.setBrewBuildId(RandomUtils.randInt(10000, 200000));
        importResult.setBrewBuildUrl(String.format("http://broo.redhat.com/bild/%d", importResult.getBrewBuildId()));
        importResult.setStatus(BuildImportStatus.SUCCESSFUL);
        result.setBuilds(singletonList(importResult));
        result.setCallback(new CallbackResultRest(callbackId, 201));
        releaseBpmCallback(result);
        assertLogContains("Brew push SUCCEEDED");

    }

    private void releaseBpmCallback(BrewPushMilestoneResultRest result) {
        givenProperPncRequest()
                .body(result)
                .when().post(String.format(BPM_NOTIFY_ENDPOINT, taskId))
                .then().statusCode(200);
    }

    private void assertBpmMockGotCalled() {
        PushList pushList = givenProperPncRequest().when().get(String.format(BPM_MANAGER_MOCK_PUSHES_FOR, milestoneId))
                .then().statusCode(200)
                .extract().body().as(PushList.class);

        List<BpmMock.Push> pushes = pushList.pushes;
        assertThat(pushes).hasSize(1);
        BpmMock.Push push = pushes.iterator().next();
        taskId = push.getTaskId();
        callbackId = push.getCallbackId();
    }

    private void assertLogContains(String message) {
        givenProperPncRequest()
                .when().get(String.format(PRODUCT_MILESTONE_SPECIFIC_REST_ENDPOINT, milestoneId))
                .then().statusCode(200)
                .body(containsJsonAttribute("pushLog",
                        value -> assertThat(value).contains(message))
                );
    }

    private void triggerMilestoneRelease() {
        ProductMilestoneRest dto = new ProductMilestoneRest();
        dto.setEndDate(new Date());
        dto.setId(milestoneId);
        givenProperPncRequest()
                .body(dto)
                .when().put(String.format(PRODUCT_MILESTONE_SPECIFIC_REST_ENDPOINT, milestoneId))
                .then().statusCode(200);
    }

    private void prepareBpmMock() {
        givenProperPncRequest().when().get(String.format(BPM_MANAGER_MOCK_EXPECT_MILESTONE, milestoneId))
                .then().statusCode(200);
    }

    private void prepareMilestone() throws IOException {
        givenProperPncRequest()
                .when().get(PRODUCT_REST_ENDPOINT).then().statusCode(200)
                .body(containsJsonAttribute(FIRST_CONTENT_ID, value -> productId = Integer.valueOf(value)));
        givenProperPncRequest()
                .when().get(String.format(PRODUCT_VERSION_REST_ENDPOINT, productId)).then().statusCode(200)
                .body(JsonMatcher.containsJsonAttribute(FIRST_CONTENT_ID, value -> productVersionId = Integer.valueOf(value)));


        JsonTemplateBuilder productMilestoneTemplate = JsonTemplateBuilder.fromResource("productMilestone_template");
        productMilestoneTemplate.addValue("_productVersionId", String.valueOf(productVersionId));

        Response response = given().headers(testHeaders)
                .body(productMilestoneTemplate.fillTemplate()).contentType(ContentType.JSON).port(getHttpPort()).when()
                .post(PRODUCT_MILESTONE_REST_ENDPOINT);
        Assertions.assertThat(response.statusCode()).isEqualTo(201);

        String location = response.getHeader("Location");

        milestoneId = Integer.valueOf(location.substring(location.lastIndexOf(PRODUCT_MILESTONE_REST_ENDPOINT)
                + PRODUCT_MILESTONE_REST_ENDPOINT.length()));
    }

    private RequestSpecification givenProperPncRequest() {
        return given().headers(testHeaders).contentType(ContentType.JSON).port(getHttpPort());
    }


}
