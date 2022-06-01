

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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.io.Files;
import com.google.gson.Gson;
import org.apache.http.HttpEntity;
import org.apache.http.HttpStatus;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.mime.HttpMultipartMode;
import org.apache.http.entity.mime.MultipartEntity;
import org.apache.http.entity.mime.content.FileBody;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.testng.Assert;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Factory;
import org.testng.annotations.Test;
import org.wso2.am.integration.clients.admin.ApiResponse;

import org.wso2.am.integration.clients.admin.api.dto.*;
import org.wso2.am.integration.test.helpers.AdminApiTestHelper;
import org.wso2.am.integration.test.impl.DtoFactory;
import org.wso2.am.integration.test.utils.APIManagerIntegrationTestException;
import org.wso2.am.integration.test.utils.base.APIMIntegrationBaseTest;
import org.wso2.am.integration.test.utils.base.APIMIntegrationConstants;
import org.wso2.am.integration.test.utils.http.HTTPSClientUtils;
import org.wso2.carbon.automation.engine.context.TestUserMode;
import org.wso2.carbon.integration.common.admin.client.UserManagementClient;

import java.io.*;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.testng.Assert.assertEquals;
import static org.wso2.am.integration.test.utils.generic.APIMTestCaseUtils.encodeCredentials;

public class ThrottlePolicyExportImportTestCase extends APIMIntegrationBaseTest {
    private final String displayName = "Test Policy";
    private String description = "This is a test throttle policy";
    private final String timeUnit = "min";
    private final String timeUnitHour = "hour";
    private final Integer unitTime = 1;
    private AdvancedThrottlePolicyDTO AdvancedPolicyDTO;

    private ApplicationThrottlePolicyDTO ApplicationPolicyDTO;
    private CustomRuleDTO CustomPolicyDTO;
    private SubscriptionThrottlePolicyDTO SubscriptionPolicyDTO;
    private ExportThrottlePolicyDTO AdvancedPolicyExportedDTO;
    private ExportThrottlePolicyDTO ApplicationPolicyExportedDTO;
    private ExportThrottlePolicyDTO SubscriptionPolicyExportedDTO;
    private ExportThrottlePolicyDTO CustomPolicyExportedDTO;
    private AdminApiTestHelper adminApiTestHelper;
    private final String ADMIN_ROLE = "admin";
    private final String ADMIN1_USERNAME = "admin1";
    private final String PASSWORD = "admin1";
    private String exportUrl;
    private String importUrl;
    private String advancedPolicyName="TestPolicyAdvanced";
    private String applicationPolicyName="TestPolicyApplication";

    private final String APIM_VERSION ="v4.1.0";
    private String THROTTLE_POLICY_TYPE ="throttling policy";
    private String ADVANCED_POLICY_SUBTYPE="advanced policy";
    private String APPLICATION_POLICY_SUBTYPE="application policy";
    private String advancedPolicyType="api";

    private String applicationPolicyType="app";

    private String advancedPolicyId;
    private File exportedFileAdvancedPolicy;
    private File exportedFileApplicationPolicy;
    private String exportRestAPIResource = "/throttling/policies/export";

    private String importRestAPIResource = "/throttling/policies/import";


    @Factory(dataProvider = "userModeDataProvider")
    public ThrottlePolicyExportImportTestCase(TestUserMode userMode) {
        this.userMode = userMode;
    }

    @DataProvider
    public static Object[][] userModeDataProvider() {
        return new Object[][]{new Object[]{TestUserMode.SUPER_TENANT_ADMIN},
                new Object[]{TestUserMode.TENANT_ADMIN}};
    }

    @BeforeClass(alwaysRun = true)
    public void setEnvironment() throws Exception {
        super.init(userMode);
        adminApiTestHelper = new AdminApiTestHelper();
        userManagementClient = new UserManagementClient(keyManagerContext.getContextUrls().getBackEndUrl(),
                keyManagerContext.getContextTenant().getTenantAdmin().getUserName(),
                keyManagerContext.getContextTenant().getTenantAdmin().getPassword());

       
        userManagementClient
                .addUser(ADMIN1_USERNAME, PASSWORD, new String[] { ADMIN_ROLE }, ADMIN1_USERNAME);

        exportUrl = adminURLHttps+ APIMIntegrationConstants.REST_API_ADMIN_CONTEXT_FULL_0+exportRestAPIResource;
        importUrl = adminURLHttps+APIMIntegrationConstants.REST_API_ADMIN_CONTEXT_FULL_0+importRestAPIResource;

        addAdvancedPolicy();
        addApplicationPolicy();
    }

    public void addAdvancedPolicy() throws Exception {

        Long requestCount = 50L;
        List<ConditionalGroupDTO> conditionalGroups = new ArrayList<>();

        RequestCountLimitDTO requestCountLimit =
                DtoFactory.createRequestCountLimitDTO(timeUnit, unitTime, requestCount);

        ThrottleLimitDTO defaultLimit =
                DtoFactory.createThrottleLimitDTO(ThrottleLimitDTO.TypeEnum.REQUESTCOUNTLIMIT, requestCountLimit, null);

        AdvancedThrottlingPolicyTestCase advancedThrottlingPolicyTestCase =new AdvancedThrottlingPolicyTestCase(userMode);
        conditionalGroups.add(advancedThrottlingPolicyTestCase.createConditionalGroup(defaultLimit));
        AdvancedPolicyDTO = DtoFactory
                .createAdvancedThrottlePolicyDTO(advancedPolicyName, displayName, description, false, defaultLimit,
                        conditionalGroups);
        //Add the advanced throttling policy
        ApiResponse<AdvancedThrottlePolicyDTO> addedPolicy =
                restAPIAdmin.addAdvancedThrottlingPolicy(AdvancedPolicyDTO);

        //Assert the status code and policy ID
        Assert.assertEquals(addedPolicy.getStatusCode(), HttpStatus.SC_CREATED);
        AdvancedThrottlePolicyDTO addedPolicyDTO = addedPolicy.getData();
        advancedPolicyId = addedPolicyDTO.getPolicyId();
        Assert.assertNotNull(advancedPolicyId, "The policy ID cannot be null or empty");

        AdvancedPolicyDTO.setPolicyId(advancedPolicyId);
        AdvancedPolicyDTO.setIsDeployed(true);
        //Verify the created advanced throttling policy DTO
        adminApiTestHelper.verifyAdvancedThrottlePolicyDTO(AdvancedPolicyDTO, addedPolicyDTO);
    }

    public void addApplicationPolicy() throws Exception {

        Long requestCount = 50L;
        RequestCountLimitDTO requestCountLimit =
                DtoFactory.createRequestCountLimitDTO(timeUnit, unitTime, requestCount);
        ThrottleLimitDTO defaultLimit =
                DtoFactory.createThrottleLimitDTO(ThrottleLimitDTO.TypeEnum.REQUESTCOUNTLIMIT, requestCountLimit, null);
         ApplicationPolicyDTO = DtoFactory.createApplicationThrottlePolicyDTO(applicationPolicyName,
                displayName, description, false, defaultLimit);

        //Add the application throttling policy
        ApiResponse<ApplicationThrottlePolicyDTO> addedPolicy =
                restAPIAdmin.addApplicationThrottlingPolicy(ApplicationPolicyDTO);

        //Assert the status code and policy ID
        Assert.assertEquals(addedPolicy.getStatusCode(), HttpStatus.SC_CREATED);
        ApplicationThrottlePolicyDTO addedPolicyDTO = addedPolicy.getData();
        String policyId = addedPolicyDTO.getPolicyId();
        Assert.assertNotNull(policyId, "The policy ID cannot be null or empty");

        ApplicationPolicyDTO.setPolicyId(policyId);
        ApplicationPolicyDTO.setIsDeployed(true);
        //Verify the created application throttling policy DTO
        adminApiTestHelper.verifyApplicationThrottlePolicyDTO(ApplicationPolicyDTO, addedPolicyDTO);
    }

    @Test(groups = { "wso2.am" }, description = "Exported Sample ThrottlePolicy with endpoint security enabled")
    public void testApplicationThrottlePolicyExport() throws Exception {

        exportedFileApplicationPolicy=exportArtifact(user.getUserName(), user.getPassword(),applicationPolicyName,applicationPolicyType);

        StringBuilder contentBuilder = new StringBuilder();
        try (Stream<String> stream = java.nio.file.Files.lines(Paths.get(String.valueOf(exportedFileApplicationPolicy)), StandardCharsets.UTF_8)) {
            stream.forEach(s -> contentBuilder.append(s).append("\n"));
        } catch (IOException e) {
            throw new APIManagerIntegrationTestException("Error in reading from extracted Throttle Policy file " + exportedFileApplicationPolicy, e);
        }

        String exportedThrottlePolicyContent = contentBuilder.toString();
        JSONParser parser = new JSONParser();
        JSONObject exportedThrottlePolicyJson = (JSONObject) parser.parse(exportedThrottlePolicyContent);
        ApplicationPolicyDTO.setIsDeployed(false);
        ApplicationPolicyDTO.setType("ApplicationThrottlePolicy");
        ApplicationPolicyExportedDTO=DtoFactory.createExportThrottlePolicyDTO(THROTTLE_POLICY_TYPE,APPLICATION_POLICY_SUBTYPE,APIM_VERSION,ApplicationPolicyDTO);

        Gson gson = new Gson();
        ObjectMapper mapper = new ObjectMapper();
        ExportThrottlePolicyDTO expectedExportedPolicy = gson.fromJson(exportedThrottlePolicyJson.toJSONString(), ExportThrottlePolicyDTO.class);
        ApplicationThrottlePolicyDTO appPolicy = mapper.convertValue(expectedExportedPolicy.getData(),
                ApplicationThrottlePolicyDTO.class);
        expectedExportedPolicy.setData(appPolicy);
        Assert.assertEquals(ApplicationPolicyExportedDTO,expectedExportedPolicy);

    }

    @Test(groups = { "wso2.am" }, description = "Exported Sample ThrottlePolicy with endpoint security enabled")
    public void testAdvancedThrottlePolicyExport() throws Exception {

        exportedFileAdvancedPolicy =exportArtifact(user.getUserName(), user.getPassword(),advancedPolicyName,advancedPolicyType);

        StringBuilder contentBuilder = new StringBuilder();
        try (Stream<String> stream = java.nio.file.Files.lines(Paths.get(String.valueOf(exportedFileAdvancedPolicy)), StandardCharsets.UTF_8)) {
            stream.forEach(s -> contentBuilder.append(s).append("\n"));
        } catch (IOException e) {
            throw new APIManagerIntegrationTestException("Error in reading from extracted Throttle Policy file " + exportedFileAdvancedPolicy, e);
        }

        String exportedThrottlePolicyContent = contentBuilder.toString();
        JSONParser parser = new JSONParser();
        JSONObject exportedThrottlePolicyJson = (JSONObject) parser.parse(exportedThrottlePolicyContent);
        AdvancedPolicyDTO.setIsDeployed(false);
        AdvancedPolicyDTO.setType("AdvancedThrottlePolicy");
        AdvancedPolicyExportedDTO=DtoFactory.createExportThrottlePolicyDTO(THROTTLE_POLICY_TYPE,ADVANCED_POLICY_SUBTYPE,APIM_VERSION,AdvancedPolicyDTO);

        Gson gson = new Gson();
        ObjectMapper mapper = new ObjectMapper();
        ExportThrottlePolicyDTO expectedExportedPolicy = gson.fromJson(exportedThrottlePolicyJson.toJSONString(), ExportThrottlePolicyDTO.class);
        AdvancedThrottlePolicyDTO advancedPolicy = mapper.convertValue(expectedExportedPolicy.getData(),
                AdvancedThrottlePolicyDTO.class);
        expectedExportedPolicy.setData(advancedPolicy);
        Assert.assertEquals(AdvancedPolicyExportedDTO,expectedExportedPolicy);

    }
    @Test(groups = { "wso2.am" }, description = "Importing new API", dependsOnMethods = "testAdvancedThrottlePolicyExport")
    public void testNewAdvancedThrottlePolicyUpdateConflict() throws Exception {
        URL importRequest =
                new URL(importUrl + "?overwrite=" + "false" );
        CloseableHttpResponse response = importArtifact(importRequest, exportedFileAdvancedPolicy, user.getUserName(), user.getPassword().toCharArray());
        Assert.assertEquals(response.getStatusLine().getStatusCode(),HttpStatus.SC_CONFLICT);
    }

    @Test(groups = { "wso2.am" }, description = "Importing new API", dependsOnMethods = "testAdvancedThrottlePolicyExport")
    public void testNewAdvancedThrottlePolicyUpdate() throws Exception {
        URL importRequest =
                new URL(importUrl + "?overwrite=" + "true" );
        CloseableHttpResponse response = importArtifact(importRequest, exportedFileAdvancedPolicy, user.getUserName(), user.getPassword().toCharArray());
        Assert.assertEquals(response.getStatusLine().getStatusCode(),HttpStatus.SC_OK);
    }

    @Test(groups = { "wso2.am" }, description = "Importing new API", dependsOnMethods = "testNewAdvancedThrottlePolicyUpdate")
    public void testNewAdvancedThrottlePolicyNew() throws Exception {

        ApiResponse<Void> ApiResponse = restAPIAdmin.deleteAdvancedThrottlingPolicy(advancedPolicyId);

        ApiResponse.getStatusCode();
        Assert.assertEquals(ApiResponse.getStatusCode(), HttpStatus.SC_OK);
        URL importRequest =
                new URL(importUrl + "?overwrite=" + "false" );
        CloseableHttpResponse response = importArtifact(importRequest, exportedFileAdvancedPolicy, user.getUserName(), user.getPassword().toCharArray());
        Assert.assertEquals(response.getStatusLine().getStatusCode(),HttpStatus.SC_CREATED);
    }

    private static CloseableHttpResponse importArtifact(URL importUrl, File fileName, String user, char[] pass) throws IOException {
        try (CloseableHttpClient client = HttpClientBuilder.create().build()) {

            HttpPost request = new HttpPost(importUrl.toURI());
            FileBody fileBody = new FileBody(fileName);
            MultipartEntity multipartEntity = new MultipartEntity(HttpMultipartMode.STRICT);
            multipartEntity.addPart("file", fileBody);
            request.setEntity(multipartEntity);

            request.setHeader("Content-Type", multipartEntity.getContentType().getValue());
            request.setHeader("Accept", "application/json");
            request.setHeader(APIMIntegrationConstants.AUTHORIZATION_HEADER,
                    "Basic " + encodeCredentials(user, pass));

            CloseableHttpResponse response =  client.execute(request);
            return response;
        } catch (URISyntaxException e) {
            throw new RuntimeException(e);
        }
    }


    private CloseableHttpResponse exportThrottlePolicyRequest(URL exportRequest, String username, String password)
            throws IOException, URISyntaxException {
        CloseableHttpClient client = HTTPSClientUtils.getHttpsClient();
        HttpGet get = new HttpGet(exportRequest.toURI());
        get.addHeader(APIMIntegrationConstants.AUTHORIZATION_HEADER, "Basic " +
                encodeCredentials(username, password.toCharArray()));
        CloseableHttpResponse response = client.execute(get);
        return response;
    }

    private File exportArtifact( String username, String password,String policyName, String policyType) throws URISyntaxException, IOException {

        //construct export Throttle Policy url
        URL exportRequest =
                new URL(exportUrl + "?name=" + policyName + "&type=" + policyType  );

        File TempDir = Files.createTempDir();

        String fileName = policyType + "_" + policyName;
        File newExportedFile = new File(TempDir.getAbsolutePath() + File.separator + fileName + ".json");

        CloseableHttpResponse response = exportThrottlePolicyRequest(exportRequest, username, password);
        HttpEntity entity = response.getEntity();
        if (entity != null) {
            FileOutputStream outStream = new FileOutputStream(newExportedFile);
            try {
                entity.writeTo(outStream);
            } finally {
                outStream.close();
            }
        }
        assertEquals(response.getStatusLine().getStatusCode(), HttpStatus.SC_OK, "Response code is not as expected");
        Assert.assertTrue(newExportedFile.exists(), "File save was not successful");
        return  newExportedFile;
    }

}
