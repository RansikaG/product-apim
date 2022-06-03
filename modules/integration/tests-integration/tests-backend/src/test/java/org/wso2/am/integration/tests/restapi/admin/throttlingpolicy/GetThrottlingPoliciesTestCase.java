/*
 *
 *   Copyright (c) 2015, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
 *
 *   WSO2 Inc. licenses this file to you under the Apache License,
 *   Version 2.0 (the "License"); you may not use this file except
 *   in compliance with the License.
 *   You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 *   Unless required by applicable law or agreed to in writing,
 *   software distributed under the License is distributed on an
 *   "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *   KIND, either express or implied.  See the License for the
 *   specific language governing permissions and limitations
 *   under the License.
 *
 */

package org.wso2.am.integration.tests.restapi.admin.throttlingpolicy;

import org.apache.http.HttpStatus;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.testng.Assert;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Factory;
import org.testng.annotations.Test;
import org.wso2.am.integration.test.helpers.AdminApiTestHelper;
import org.wso2.am.integration.test.utils.base.APIMIntegrationBaseTest;
import org.wso2.am.integration.test.utils.base.APIMIntegrationConstants;
import org.wso2.carbon.automation.engine.context.TestUserMode;
import org.wso2.carbon.integration.common.admin.client.UserManagementClient;
import java.net.URL;
import static org.wso2.am.integration.test.utils.generic.APIMTestCaseUtils.encodeCredentials;

public class GetThrottlingPoliciesTestCase extends APIMIntegrationBaseTest {
    private final String ADMIN1_USERNAME = "admin1";
    private final String PASSWORD = "admin1";
    private final String ADMIN_ROLE = "admin";
    private AdminApiTestHelper adminApiTestHelper;
    private final String getThrottlePoliciesResource = "/throttling/policies/search";

    private String getThrottlePoliciesUrl;

    @Factory(dataProvider = "userModeDataProvider")
    public GetThrottlingPoliciesTestCase(TestUserMode userMode) {
        this.userMode = userMode;
    }

    @DataProvider public static Object[][] userModeDataProvider() {
        return new Object[][] { new Object[] { TestUserMode.SUPER_TENANT_ADMIN },
                new Object[] { TestUserMode.TENANT_ADMIN } };
    }

    @BeforeClass(alwaysRun = true) public void setEnvironment() throws Exception {
        super.init(userMode);
        adminApiTestHelper = new AdminApiTestHelper();
        userManagementClient = new UserManagementClient(keyManagerContext.getContextUrls().getBackEndUrl(),
                keyManagerContext.getContextTenant().getTenantAdmin().getUserName(),
                keyManagerContext.getContextTenant().getTenantAdmin().getPassword());
        userManagementClient.addUser(ADMIN1_USERNAME, PASSWORD, new String[] { ADMIN_ROLE }, ADMIN1_USERNAME);

        getThrottlePoliciesUrl =
                adminURLHttps + APIMIntegrationConstants.REST_API_ADMIN_CONTEXT_FULL_0 + getThrottlePoliciesResource;
    }

    @Test(groups = { "wso2.am" }, description = "Exported Sample ThrottlePolicy with endpoint security enabled")
    public void testThrottlePolicyExport() throws Exception {
        try (CloseableHttpClient client = HttpClientBuilder.create().build()) {
        //construct get Throttle Policies url
        URL exportRequest =
                new URL(getThrottlePoliciesUrl);
        HttpGet request = new HttpGet(exportRequest.toURI());
        request.setHeader("Content-Type", "application/json");
        request.setHeader("Accept", "application/json");
        request.setHeader(APIMIntegrationConstants.AUTHORIZATION_HEADER,
                "Basic " + encodeCredentials(user.getUserName(),user.getPassword().toCharArray()));
        CloseableHttpResponse response =  client.execute(request);
        Assert.assertEquals(response.getStatusLine().getStatusCode(), HttpStatus.SC_OK);
        }
    }
}
