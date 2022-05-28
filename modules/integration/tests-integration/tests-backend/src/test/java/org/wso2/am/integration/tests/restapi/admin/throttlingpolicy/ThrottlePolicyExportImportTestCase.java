package org.wso2.am.integration.tests.restapi.admin.throttlingpolicy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.io.Files;
import com.google.gson.Gson;
import org.apache.http.HttpEntity;
import org.apache.http.HttpStatus;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;
import org.testng.Assert;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Factory;
import org.testng.annotations.Test;
import org.wso2.am.integration.clients.admin.ApiResponse;

import org.wso2.am.integration.clients.admin.api.dto.*;
import org.wso2.am.integration.clients.store.api.v1.dto.ApplicationKeyDTO;
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
    private String displayName = "Test Policy";
    private String description = "This is a test advanced throttle policy";
    private String timeUnit = "min";
    private String timeUnitHour = "hour";
    private Integer unitTime = 1;
    private AdvancedThrottlePolicyDTO AdvancedPolicyDTO;
    private ExportThrottlePolicyDTO AdvancedPolicyExportedDTO;
    private AdminApiTestHelper adminApiTestHelper;
    private final String ADMIN_ROLE = "admin";
    private final String ADMIN1_USERNAME = "admin1";
    private final String ADMIN2_USERNAME = "admin2";
    private final String PASSWORD = "admin1";
    private String exportUrl;
    private String importUrl;
    private String advancedPolicyName="TestPolicyAdvanced";

    private final String APIM_VERSION ="v4.1.0";
    private String THROTTLE_POLICY_TYPE ="throttling policy";
    private String ADVANCED_POLICY_SUBTYPE="advanced policy";
    private String advancedPolicyType="api";

    private String advancedPolicyId;
    private File exportedFile;
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
//        userManagementClient
//                .addUser(ADMIN2_USERNAME, PASSWORD, new String[] { ADMIN_ROLE }, ADMIN2_USERNAME);

        exportUrl = adminURLHttps+ APIMIntegrationConstants.REST_API_ADMIN_CONTEXT_FULL_0+exportRestAPIResource;
        importUrl = adminURLHttps+APIMIntegrationConstants.REST_API_ADMIN_CONTEXT_FULL_0+importRestAPIResource;
    }

    @Test(groups = {"wso2.am"}, description = "Test add advanced throttling policy with all the details")
    public void testAddAdvancedPolicy() throws Exception {

        //Create the advanced throttling policy DTO with request count limit
        String policyName = "TestPolicyAdvanced";
        Long requestCount = 50L;

        List<ConditionalGroupDTO> conditionalGroups = new ArrayList<>();


        RequestCountLimitDTO requestCountLimit =
                DtoFactory.createRequestCountLimitDTO(timeUnit, unitTime, requestCount);

        ThrottleLimitDTO defaultLimit =
                DtoFactory.createThrottleLimitDTO(ThrottleLimitDTO.TypeEnum.REQUESTCOUNTLIMIT, requestCountLimit, null);

        AdvancedThrottlingPolicyTestCase advancedThrottlingPolicyTestCase =new AdvancedThrottlingPolicyTestCase(userMode);
        conditionalGroups.add(advancedThrottlingPolicyTestCase.createConditionalGroup(defaultLimit));
        AdvancedPolicyDTO = DtoFactory
                .createAdvancedThrottlePolicyDTO(policyName, displayName, description, false, defaultLimit,
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

    @Test(groups = { "wso2.am" }, description = "Exported Sample ThrottlePolicy with endpoint security enabled", dependsOnMethods ="testAddAdvancedPolicy")
    public void testThrottlePolicyExport() throws Exception {


        //construct export Throttle Policy url
        URL exportRequest =
                new URL(exportUrl + "?name=" + advancedPolicyName + "&type=" + advancedPolicyType + "&format=JSON" );

        File TempDir = Files.createTempDir();

        String fileName = advancedPolicyType + "_" + advancedPolicyName;
        exportedFile = new File(TempDir.getAbsolutePath() + File.separator + fileName + ".json");
        //save the exported Throttle Policy
        exportArtifact(exportRequest, exportedFile, user.getUserName(), user.getPassword());


        StringBuilder contentBuilder = new StringBuilder();
        try (Stream<String> stream = java.nio.file.Files.lines(Paths.get(String.valueOf(exportedFile)), StandardCharsets.UTF_8)) {
            stream.forEach(s -> contentBuilder.append(s).append("\n"));
        } catch (IOException e) {
            throw new APIManagerIntegrationTestException("Error in reading from extracted Throttle Policy file " + exportedFile, e);
        }

        String exportedThrottlePolicyContent = contentBuilder.toString();
        JSONParser parser = new JSONParser();
        JSONObject exportedThrottlePolicyJson = (JSONObject) parser.parse(exportedThrottlePolicyContent);
        AdvancedPolicyDTO.setIsDeployed(false);
        AdvancedPolicyDTO.setType("AdvancedThrottlePolicy");
        AdvancedPolicyExportedDTO=DtoFactory.createExportThrottlePolicyDTO(THROTTLE_POLICY_TYPE,ADVANCED_POLICY_SUBTYPE,APIM_VERSION,AdvancedPolicyDTO);

        Gson g = new Gson();
        ObjectMapper mapper = new ObjectMapper();
        ExportThrottlePolicyDTO expectedExportedPolicy = g.fromJson(exportedThrottlePolicyJson.toJSONString(), ExportThrottlePolicyDTO.class);
        AdvancedThrottlePolicyDTO advancedPolicy = mapper.convertValue(expectedExportedPolicy.getData(),
                AdvancedThrottlePolicyDTO.class);
        expectedExportedPolicy.setData(advancedPolicy);
        Assert.assertEquals(AdvancedPolicyExportedDTO,expectedExportedPolicy);

    }
    @Test(groups = { "wso2.am" }, description = "Importing new API", dependsOnMethods = "testThrottlePolicyExport")
    public void testNewThrottlePolicyUpdateConflict() throws Exception {
        URL importRequest =
                new URL(importUrl + "?overwrite=" + "false" );
        CloseableHttpResponse response=importArtifact(importRequest, exportedFile, user.getUserName(), user.getPassword().toCharArray());
        Assert.assertEquals(response.getStatusLine().getStatusCode(),HttpStatus.SC_CONFLICT);
    }

    @Test(groups = { "wso2.am" }, description = "Importing new API", dependsOnMethods = "testThrottlePolicyExport")
    public void testNewThrottlePolicyUpdate() throws Exception {
        URL importRequest =
                new URL(importUrl + "?overwrite=" + "true" );
        CloseableHttpResponse response=importArtifact(importRequest, exportedFile, user.getUserName(), user.getPassword().toCharArray());
        Assert.assertEquals(response.getStatusLine().getStatusCode(),HttpStatus.SC_OK);
    }

    @Test(groups = { "wso2.am" }, description = "Importing new API", dependsOnMethods = "testNewThrottlePolicyUpdate")
    public void testNewThrottlePolicyNew() throws Exception {

        ApiResponse<Void> ApiResponse = restAPIAdmin.deleteAdvancedThrottlingPolicy(advancedPolicyId);

        ApiResponse.getStatusCode();
        Assert.assertEquals(ApiResponse.getStatusCode(), HttpStatus.SC_OK);
        URL importRequest =
                new URL(importUrl + "?overwrite=" + "false" );
        CloseableHttpResponse response=importArtifact(importRequest, exportedFile, user.getUserName(), user.getPassword().toCharArray());
        Assert.assertEquals(response.getStatusLine().getStatusCode(),HttpStatus.SC_CREATED);
    }


    private static CloseableHttpResponse importArtifact(URL importUrl, File fileName, String user, char[] pass) throws IOException {
        try (CloseableHttpClient client = HttpClientBuilder.create().build()) {

            JSONParser parser = new JSONParser();
            Object obj;
            try {
                obj = parser.parse(new FileReader(fileName));
            } catch (ParseException e) {
                throw new RuntimeException(e);
            }
            JSONObject jsonObject = (JSONObject)obj;

            HttpPost request = new HttpPost(importUrl.toURI());

            request.setHeader("Content-Type", "application/json");
            request.setHeader("Accept", "application/json");
            request.setHeader(APIMIntegrationConstants.AUTHORIZATION_HEADER,
                                   "Basic " + encodeCredentials(user, pass));
            StringEntity entity = new StringEntity(jsonObject.toJSONString());
            request.setEntity(entity);

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

    private void exportArtifact(URL exportRequest, File fileName, String username, String password) throws URISyntaxException, IOException {
        CloseableHttpResponse response = exportThrottlePolicyRequest(exportRequest, username, password);
        HttpEntity entity = response.getEntity();
        if (entity != null) {
            FileOutputStream outStream = new FileOutputStream(fileName);
            try {
                entity.writeTo(outStream);
            } finally {
                outStream.close();
            }
        }

        assertEquals(response.getStatusLine().getStatusCode(), HttpStatus.SC_OK, "Response code is not as expected");
        Assert.assertTrue(fileName.exists(), "File save was not successful");
    }

}
