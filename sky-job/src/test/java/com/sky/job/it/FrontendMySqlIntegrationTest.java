package com.sky.job.it;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.test.util.TestPropertyValues;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.DriverManager;
import java.sql.Statement;
import java.time.Duration;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.sql.DataSource;
import com.sky.job.plugin.callback.PluginCallbackService;
import com.sky.job.plugin.security.PluginCapability;
import com.sky.job.plugin.security.PluginRuntimePrincipal;
import com.sky.plugin.protocol.AccountRequest;
import org.apache.commons.csv.CSVFormat;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.dao.DataIntegrityViolationException;
import java.io.ByteArrayInputStream;
import java.io.StringReader;
import java.time.Instant;
import java.util.ArrayList;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Tag("frontend-integration")
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@ContextConfiguration(initializers = FrontendMySqlIntegrationTest.DatabaseInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class FrontendMySqlIntegrationTest {
    private static final String DATABASE_NAME = "sky_new_frontend_it";
    private static final String ADMIN_USERNAME = "frontend_it_admin";
    private static final String ADMIN_PASSWORD = "FrontendIt!2026";
    private static final String PLUGIN_ID = "frontend-integration";
    private static final String SERVICE_PLUGIN_ID = "frontend-integration-service";
    private static final String SERVICE_OPERATION = "echo";
    private static final String CHANNEL_A = "frontend-it-channel-a";
    private static final String CHANNEL_B = "frontend-it-channel-b";
    private static final String RULE_ID = "frontend-it-job-success";

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private DataSource dataSource;
    @Autowired
    private PluginCallbackService callbacks;

    @Test
    void verifiesFrontendBackendPermissionAndInstallationContractsAgainstRealMysql() throws Exception {
        String adminId = registerAdmin();
        String adminToken = login(ADMIN_USERNAME, ADMIN_PASSWORD);

        DepartmentFixture departments = verifyDepartmentAndDisabledCompatibility(adminToken);
        verifyCustomRoleKeepsDisabledDepartment(adminToken, departments.disabledDepartmentId());
        verifyDepartmentErrorContracts(adminToken, departments.enabledDepartmentId());

        uploadPluginDefinition(adminToken);
        uploadServicePluginDefinition(adminToken);
        verifyLocalDependencyRepositoryAndDefinitionInstall(adminToken);

        String installationA = createInstallation(adminToken, "Frontend IT A");
        String installationB = createInstallation(adminToken, "Frontend IT B");
        String serviceInstallation = createInstallation(adminToken, SERVICE_PLUGIN_ID, "Frontend IT Echo Service");
        verifyPageTotal(adminToken, "/plugins/installations?page=1&size=1", 3);

        createNotificationChannels(adminToken);
        replaceInstallationChannels(adminToken, installationA, List.of(CHANNEL_A));
        replaceInstallationChannels(adminToken, installationB, List.of(CHANNEL_B));
        assertThat(get(adminToken, "/plugins/installations/" + installationA + "/notification-channels").path("data"))
                .extracting(JsonNode::asText)
                .containsExactly(CHANNEL_A);
        assertThat(get(adminToken, "/plugins/installations/" + installationB + "/notification-channels").path("data"))
                .extracting(JsonNode::asText)
                .containsExactly(CHANNEL_B);

        verifyInstallationConfigAccountSchemaAndLifecycleIsolation(adminToken, installationA, installationB);
        long jobId = verifyJobChannelAndRuleContracts(adminToken, installationA, installationB);
        verifyServicePermissionCrud(adminToken, installationB, serviceInstallation);
        long successfulExecutionId = runAndAwait(adminToken, jobId, Map.of(), "SUCCESS");
        verifyExecutionLogIsolation(adminToken, successfulExecutionId, installationB);

        JsonNode historyBeforeTransfer = waitForHistory(adminToken, jobId);
        String snapshotSourceOwner = historyBeforeTransfer.path("sourceOwnerUserId").asText();
        long snapshotSourceDepartment = historyBeforeTransfer.path("sourceOwnerDepartmentId").asLong();
        String snapshotInstallation = historyBeforeTransfer.path("installationId").asText();
        assertThat(snapshotSourceOwner).isEqualTo(adminId);
        assertThat(snapshotSourceDepartment).isEqualTo(1L);
        assertThat(snapshotInstallation).isEqualTo(installationB);

        int historyCount = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM notification_send_history", Integer.class);
        runAndAwait(adminToken, jobId, Map.of(
                "inputJson", "{\"channelId\":\"" + CHANNEL_A + "\"}",
                "timeoutMillis", 30_000
        ), "FAILED");
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM notification_send_history", Integer.class))
                .isEqualTo(historyCount);

        TargetFixture target = createSelfScopedTargetUser(adminToken, departments.enabledDepartmentId());
        assertPageTotal(target.token(), "/plugins/installations?page=1&size=20", 0);
        JsonNode transfer = post(adminToken, "/system/data-ownership/transfer", Map.of(
                "targetUserId", target.userId(),
                "pluginInstallationIds", List.of(installationB),
                "jobIds", List.of(jobId),
                "notificationChannelIds", List.of(CHANNEL_B),
                "notificationRuleIds", List.of(RULE_ID)
        )).path("data");
        assertThat(transfer.path("targetDepartmentId").asLong()).isEqualTo(departments.enabledDepartmentId());
        assertThat(transfer.path("pluginInstallationsTransferred").asInt()).isEqualTo(1);
        assertThat(transfer.path("jobsTransferred").asInt()).isEqualTo(1);
        assertThat(transfer.path("notificationChannelsTransferred").asInt()).isEqualTo(1);
        assertThat(transfer.path("notificationRulesTransferred").asInt()).isEqualTo(1);

        assertPageTotal(target.token(), "/plugins/installations?page=1&size=20", 1);
        assertPageTotal(target.token(), "/jobs?page=1&size=20", 1);
        JsonNode me = get(target.token(), "/user/me").path("data");
        assertThat(me.path("departmentId").asLong()).isEqualTo(departments.enabledDepartmentId());
        assertThat(me.path("permissions").toString()).contains("plugin:runtime:read");

        JsonNode targetPreference = get(target.token(), "/plugins/installations/" + installationB + "/accounts/view-preference").path("data");
        assertThat(targetPreference.path("fields").isEmpty()).isTrue();
        put(target.token(), "/plugins/installations/" + installationB + "/accounts/view-preference", Map.of(
                "fields", List.of("region")
        ));
        assertThat(get(adminToken, "/plugins/installations/" + installationB + "/accounts/view-preference")
                .path("data").path("fields").isEmpty()).isTrue();
        assertThat(get(target.token(), "/plugins/installations/" + installationB + "/accounts?page=1&size=20")
                .path("data").path("items").get(0).path("displayValues").path("region").asText()).isEqualTo("west");

        long servicePermissionId = createServicePermission(adminToken, installationB, serviceInstallation);
        long serviceExecutionId = runAndAwait(adminToken, jobId, Map.of(
                "inputJson", "{\"serviceInstallationId\":\"" + serviceInstallation + "\"}",
                "timeoutMillis", 30_000
        ), "SUCCESS");
        verifyServiceCallLog(adminToken, jobId, serviceExecutionId, installationB, serviceInstallation, servicePermissionId);

        JsonNode persistedHistory = get(adminToken, "/notifications/history?jobId=" + jobId + "&page=1&size=20")
                .path("data").path("items").get(0);
        assertThat(persistedHistory.path("sourceOwnerUserId").asText()).isEqualTo(snapshotSourceOwner);
        assertThat(persistedHistory.path("sourceOwnerDepartmentId").asLong()).isEqualTo(snapshotSourceDepartment);
        assertThat(persistedHistory.path("installationId").asText()).isEqualTo(snapshotInstallation);

        verifyAccountGroups(adminToken, target.token(), installationB, serviceInstallation);
        verifyManualSeedIsIdempotent();
    }

    private void verifyAccountGroups(String token, String scopedToken, String foreignInstallation, String serviceInstallation) throws Exception {
        String installation = createInstallation(token, "Account Groups IT");
        String base = "/plugins/installations/" + installation;
        String foreignBase = "/plugins/installations/" + foreignInstallation;
        long group = post(token, base + "/account-groups", Map.of("name", " Primary ")).path("data").path("groupId").asLong();
        long backup = post(token, base + "/account-groups", Map.of("name", "Backup")).path("data").path("groupId").asLong();
        long foreignGroup = post(token, foreignBase + "/account-groups", Map.of("name", "Primary")).path("data").path("groupId").asLong();
        assertThat(get(token, base + "/account-groups").path("data")).extracting(n -> n.path("groupId").asLong()).containsExactly(group, backup);
        expectError(HttpMethod.POST, token, base + "/account-groups", Map.of("name", "primary"), HttpStatus.CONFLICT, 409001, "already exists");
        expectError(HttpMethod.POST, token, base + "/account-groups", Map.of("name", " "), HttpStatus.BAD_REQUEST, 400001, "Group name");
        expectError(HttpMethod.PUT, token, base + "/account-groups/" + backup, Map.of("name", "Primary"), HttpStatus.CONFLICT, 409001, "already exists");
        assertThat(put(token, base + "/account-groups/" + backup, Map.of("name", "Renamed")).path("data").path("name").asText()).isEqualTo("Renamed");
        expectError(HttpMethod.GET, scopedToken, base + "/account-groups", null, HttpStatus.NOT_FOUND, 404001, "installation not found");
        expectError(HttpMethod.GET, token, "/plugins/installations/" + serviceInstallation + "/account-groups", null, HttpStatus.BAD_REQUEST, 400001, "account management");

        createGroupedAccount(token, base, "z-older", group, true);
        createGroupedAccount(token, base, "a-middle", group, true);
        createGroupedAccount(token, base, "z-tie", null, true);
        createGroupedAccount(token, base, "a-tie", null, false);
        jdbcTemplate.update("UPDATE plugin_account SET created_at='2026-01-01 00:00:00' WHERE installation_id=? AND account_id='z-older'", installation);
        jdbcTemplate.update("UPDATE plugin_account SET created_at='2026-01-02 00:00:00' WHERE installation_id=? AND account_id='a-middle'", installation);
        jdbcTemplate.update("UPDATE plugin_account SET created_at='2026-01-03 00:00:00' WHERE installation_id=? AND account_id IN ('z-tie','a-tie')", installation);
        List<String> order = List.of("z-older", "a-middle", "z-tie", "a-tie");
        List<String> paged = new ArrayList<>();
        for (int page = 1; page <= 4; page++) {
            JsonNode result = get(token, base + "/accounts?size=1&page=" + page).path("data");
            assertThat(result.path("total").asInt()).isEqualTo(4);
            paged.add(result.path("items").get(0).path("accountId").asText());
        }
        assertThat(paged).containsExactlyElementsOf(order);
        JsonNode grouped = get(token, base + "/accounts?groupId=" + group + "&enabled=true&displayName=Account&accountId=older").path("data");
        assertThat(grouped.path("total").asLong()).isEqualTo(1);
        assertThat(grouped.path("items").get(0).path("groupName").asText()).isEqualTo("Primary");
        assertThat(grouped.path("items").get(0).has("accountJson")).isFalse();
        assertPageTotal(token, base + "/accounts?ungrouped=true", 2);
        assertPageTotal(token, base + "/accounts?ungrouped=true&enabled=false", 1);
        expectError(HttpMethod.GET, token, base + "/accounts?groupId=" + group + "&ungrouped=true", null, HttpStatus.BAD_REQUEST, 400001, "cannot be combined");
        expectError(HttpMethod.GET, token, base + "/accounts?groupId=" + foreignGroup, null, HttpStatus.NOT_FOUND, 404001, "group not found");
        expectError(HttpMethod.PATCH, token, base + "/accounts/z-older/group", Map.of(), HttpStatus.BAD_REQUEST, 400001, "field is required");
        expectError(HttpMethod.PATCH, token, base + "/accounts/z-older/group", Map.of("groupId", foreignGroup), HttpStatus.NOT_FOUND, 404001, "group not found");
        expectError(HttpMethod.POST, token, base + "/accounts", Map.of("accountId", "foreign-new", "accountJson", "{}", "groupId", foreignGroup), HttpStatus.NOT_FOUND, 404001, "group not found");
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM plugin_account WHERE installation_id=? AND account_id='foreign-new'", Integer.class, installation)).isZero();
        assertThatThrownBy(() -> jdbcTemplate.update("UPDATE plugin_account SET group_id=? WHERE installation_id=? AND account_id='z-older'", foreignGroup, installation))
                .isInstanceOf(DataIntegrityViolationException.class);

        String createdAt = get(token, base + "/accounts/z-older").path("data").path("createdAt").asText();
        put(token, base + "/accounts/z-older", Map.of("accountJson", "{\"accountId\":\"z-older\",\"region\":\"west\"}"));
        assertThat(get(token, base + "/accounts/z-older").path("data").path("groupId").asLong()).isEqualTo(group);
        callbacks.updateAccount(new PluginRuntimePrincipal(installation, PLUGIN_ID, "group-it-runner", null, null,
                        Set.of(PluginCapability.ACCOUNT), Instant.now(), Instant.now().plusSeconds(60)),
                AccountRequest.newBuilder().setPluginId(PLUGIN_ID).setAccountId("z-older").setAccountJson("{\"accountId\":\"z-older\",\"region\":\"east\"}").build());
        assertThat(get(token, base + "/accounts/z-older").path("data").path("groupId").asLong()).isEqualTo(group);
        callbacks.updateAccount(new PluginRuntimePrincipal(installation, PLUGIN_ID, "group-it-runner", null, null,
                        Set.of(PluginCapability.ACCOUNT), Instant.now(), Instant.now().plusSeconds(60)),
                AccountRequest.newBuilder().setAccountId("callback-new").setAccountJson("{\"accountId\":\"callback-new\"}").build());
        assertThat(get(token, base + "/accounts/callback-new").path("data").path("groupId").isNull()).isTrue();
        request(HttpMethod.DELETE, token, base + "/accounts/callback-new", null, HttpStatus.OK);

        byte[] csv = "accountId,secret,region,expireAt,enabled\nz-older,,west,,true\nimport-new,,east,,true\n".getBytes(StandardCharsets.UTF_8);
        ByteArrayResource file = new ByteArrayResource(csv) { @Override public String getFilename() { return "accounts.csv"; } };
        assertThat(multipart(token, base + "/accounts/import", Map.of("file", file)).path("data").path("successRows").asInt()).isEqualTo(2);
        assertThat(get(token, base + "/accounts/z-older").path("data").path("groupId").asLong()).isEqualTo(group);
        assertThat(get(token, base + "/accounts/import-new").path("data").path("groupId").isNull()).isTrue();
        request(HttpMethod.DELETE, token, base + "/accounts/import-new", null, HttpStatus.OK);
        assertThat(get(token, base + "/accounts/z-older").path("data").path("createdAt").asText()).isEqualTo(createdAt);
        assertThat(get(token, base + "/accounts").path("data").path("items")).extracting(n -> n.path("accountId").asText()).containsExactlyElementsOf(order);
        for (String format : List.of("csv", "xlsx")) {
            assertThat(exportedAccountIds(token, base + "/accounts/export?format=" + format, format)).containsExactlyElementsOf(order);
            assertThat(exportedAccountIds(token, base + "/accounts/export?format=" + format + "&groupId=" + group, format)).containsExactly("z-older", "a-middle");
            assertThat(exportedAccountIds(token, base + "/accounts/export?format=" + format + "&ungrouped=true&enabled=false", format)).containsExactly("a-tie");
        }
        JsonNode moved = request(HttpMethod.PATCH, token, base + "/accounts/group",
                Map.of("accountIds", List.of("z-tie", "z-tie", "missing", ""), "groupId", backup), HttpStatus.OK).path("data");
        assertThat(moved.path("succeeded").asInt()).isEqualTo(1);
        assertThat(moved.path("failed").asInt()).isEqualTo(3);
        request(HttpMethod.PATCH, token, base + "/accounts/a-middle/group", Map.of("groupId", backup), HttpStatus.OK);
        Map<String, Object> clear = new LinkedHashMap<>();
        clear.put("groupId", null);
        request(HttpMethod.PATCH, token, base + "/accounts/a-middle/group", clear, HttpStatus.OK);
        clear.put("accountIds", List.of("z-tie"));
        assertThat(request(HttpMethod.PATCH, token, base + "/accounts/group", clear, HttpStatus.OK).path("data").path("succeeded").asInt()).isEqualTo(1);
        assertThat(get(token, base + "/account-groups").path("data").get(0).path("accountCount").asLong()).isEqualTo(1);
        request(HttpMethod.DELETE, token, base + "/account-groups/" + group, null, HttpStatus.OK);
        assertThat(get(token, base + "/accounts/z-older").path("data").path("groupId").isNull()).isTrue();
        assertThat(get(token, base + "/accounts/z-older").path("data").path("groupName").isNull()).isTrue();
        assertPageTotal(token, base + "/accounts?ungrouped=true", 4);
        request(HttpMethod.PATCH, token, base + "/accounts/z-older/group", Map.of("groupId", backup), HttpStatus.OK);
        request(HttpMethod.DELETE, token, base, null, HttpStatus.OK);
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM plugin_account_group WHERE installation_id=?", Integer.class, installation)).isZero();
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM plugin_account WHERE installation_id=?", Integer.class, installation)).isZero();
        request(HttpMethod.DELETE, token, foreignBase + "/account-groups/" + foreignGroup, null, HttpStatus.OK);
    }

    private void createGroupedAccount(String token, String base, String id, Long groupId, boolean enabled) throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("accountId", id);
        body.put("displayName", "Account " + id);
        body.put("accountJson", "{\"accountId\":\"" + id + "\"}");
        body.put("enabled", enabled);
        if (groupId != null) body.put("groupId", groupId);
        assertThat(post(token, base + "/accounts", body).path("data").path("groupId").isNull()).isEqualTo(groupId == null);
    }

    private List<String> exportedAccountIds(String token, String path, String format) throws Exception {
        ResponseEntity<byte[]> response = restTemplate.exchange(url(path), HttpMethod.GET, new HttpEntity<>(headers(token)), byte[].class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        List<String> ids = new ArrayList<>();
        if (format.equals("csv")) {
            try (var csv = CSVFormat.DEFAULT.builder().setHeader().setSkipHeaderRecord(true).build()
                    .parse(new StringReader(new String(response.getBody(), StandardCharsets.UTF_8).replace("\uFEFF", "")))) {
                assertThat(csv.getHeaderNames()).doesNotContain("groupId", "groupName");
                csv.forEach(row -> ids.add(row.get("accountId")));
            }
        } else {
            try (var workbook = new XSSFWorkbook(new ByteArrayInputStream(response.getBody()))) {
                var sheet = workbook.getSheetAt(0);
                for (int row = 1; row <= sheet.getLastRowNum(); row++) ids.add(sheet.getRow(row).getCell(0).getStringCellValue());
            }
        }
        return ids;
    }

    private String registerAdmin() throws Exception {
        JsonNode response = post(null, "/user/register", Map.of(
                "username", ADMIN_USERNAME,
                "password", ADMIN_PASSWORD,
                "email", "frontend-it-admin@example.test"
        ));
        return response.path("data").path("userId").asText();
    }

    private String login(String username, String password) throws Exception {
        return post(null, "/user/login", Map.of("username", username, "password", password))
                .path("data").path("token").asText();
    }

    private DepartmentFixture verifyDepartmentAndDisabledCompatibility(String token) throws Exception {
        long enabledDepartmentId = post(token, "/system/departments", Map.of(
                "parentId", 1,
                "departmentName", "Frontend IT Enabled",
                "sortOrder", 10,
                "enabled", true
        )).path("data").path("departmentId").asLong();
        long disabledDepartmentId = post(token, "/system/departments", Map.of(
                "parentId", 1,
                "departmentName", "Frontend IT Legacy Disabled",
                "sortOrder", 20,
                "enabled", true
        )).path("data").path("departmentId").asLong();
        String legacyUserId = post(token, "/system/users", Map.of(
                "username", "frontend_it_legacy",
                "password", ADMIN_PASSWORD,
                "email", "frontend-it-legacy@example.test",
                "departmentId", disabledDepartmentId,
                "enabled", true,
                "roles", List.of()
        )).path("data").path("userId").asText();

        jdbcTemplate.update("UPDATE sky_department SET enabled=FALSE WHERE department_id=?", disabledDepartmentId);
        JsonNode disabledNode = findDepartment(get(token, "/system/departments").path("data"), disabledDepartmentId);
        assertThat(disabledNode).isNotNull();
        assertThat(disabledNode.path("enabled").asBoolean()).isFalse();

        JsonNode legacyUser = get(token, "/system/users/" + legacyUserId).path("data");
        assertThat(legacyUser.path("departmentId").asLong()).isEqualTo(disabledDepartmentId);
        JsonNode updated = put(token, "/system/users/" + legacyUserId, Map.of(
                "email", "frontend-it-legacy@example.test",
                "departmentId", enabledDepartmentId,
                "enabled", true
        )).path("data");
        assertThat(updated.path("departmentId").asLong()).isEqualTo(enabledDepartmentId);
        return new DepartmentFixture(enabledDepartmentId, disabledDepartmentId);
    }

    private void verifyCustomRoleKeepsDisabledDepartment(String token, long disabledDepartmentId) throws Exception {
        long roleId = post(token, "/system/roles", Map.of(
                "roleName", "FRONTEND_IT_CUSTOM",
                "description", "Keeps a disabled department id"
        )).path("data").path("roleId").asLong();
        JsonNode updated = put(token, "/system/roles/" + roleId + "/data-scope", Map.of(
                "dataScope", "CUSTOM",
                "departmentIds", List.of(disabledDepartmentId)
        )).path("data");
        assertThat(updated.path("departmentIds").toString()).contains(Long.toString(disabledDepartmentId));
        JsonNode loaded = get(token, "/system/roles/" + roleId).path("data");
        assertThat(loaded.path("departmentIds").toString()).contains(Long.toString(disabledDepartmentId));
        expectError(HttpMethod.PUT, token, "/system/roles/" + roleId + "/data-scope", Map.of(
                "dataScope", "CUSTOM",
                "departmentIds", List.of(99999999)
        ), HttpStatus.NOT_FOUND, 404001, "Department not found");
    }

    private void verifyDepartmentErrorContracts(String token, long enabledDepartmentId) throws Exception {
        expectError(HttpMethod.GET, token, "/system/departments/99999999", null, HttpStatus.NOT_FOUND, 404001, "Department not found");
        expectError(HttpMethod.POST, token, "/system/departments", Map.of(
                "parentId", 99999999,
                "departmentName", "Frontend IT Missing Parent",
                "enabled", true
        ), HttpStatus.NOT_FOUND, 404001, "Parent department not found");
        expectError(HttpMethod.POST, token, "/system/users", Map.of(
                "username", "frontend_it_missing_department",
                "password", ADMIN_PASSWORD,
                "email", "frontend-it-missing-department@example.test",
                "departmentId", 99999999,
                "enabled", true,
                "roles", List.of()
        ), HttpStatus.NOT_FOUND, 404001, "Department not found");
        expectError(HttpMethod.POST, token, "/system/departments", Map.of(
                "parentId", 1,
                "departmentName", "Frontend IT Enabled",
                "enabled", true
        ), HttpStatus.BAD_REQUEST, 400001, "Department name already exists under the parent");
        long childId = post(token, "/system/departments", Map.of(
                "parentId", enabledDepartmentId,
                "departmentName", "Frontend IT Child",
                "enabled", true
        )).path("data").path("departmentId").asLong();
        expectError(HttpMethod.PUT, token, "/system/departments/" + enabledDepartmentId, Map.of(
                "parentId", childId,
                "departmentName", "Frontend IT Enabled",
                "enabled", true
        ), HttpStatus.BAD_REQUEST, 400001, "Department hierarchy cannot contain a cycle");
        expectError(HttpMethod.PUT, token, "/system/departments/1", Map.of(
                "parentId", enabledDepartmentId,
                "departmentName", "Default Department",
                "enabled", true
        ), HttpStatus.CONFLICT, 409001, "Root department cannot have a parent");
    }

    private void uploadPluginDefinition(String token) throws Exception {
        Path pluginJar = requiredFile("sky.it.plugin.jar");
        JsonNode upload = multipart(token, "/plugins/artifacts", Map.of("file", new FileSystemResource(pluginJar)));
        assertThat(upload.path("data").path("pluginId").asText()).isEqualTo(PLUGIN_ID);
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM plugin_installation WHERE plugin_id=?", Integer.class, PLUGIN_ID)).isZero();
    }

    private void uploadServicePluginDefinition(String token) throws Exception {
        Path pluginJar = requiredFile("sky.it.service.plugin.jar");
        JsonNode upload = multipart(token, "/plugins/artifacts", Map.of("file", new FileSystemResource(pluginJar)));
        assertThat(upload.path("data").path("pluginId").asText()).isEqualTo(SERVICE_PLUGIN_ID);
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM plugin_installation WHERE plugin_id=?", Integer.class, SERVICE_PLUGIN_ID)).isZero();
    }

    private void verifyLocalDependencyRepositoryAndDefinitionInstall(String token) throws Exception {
        Path dependencyJar = requiredFile("sky.it.dependency.jar");
        JsonNode upload = multipart(token, "/plugins/dependencies/local-artifacts", Map.of(
                "file", new FileSystemResource(dependencyJar),
                "groupId", "com.sky.it",
                "artifactId", "frontend-helper",
                "version", "1.0.0"
        ));
        String coordinate = upload.path("data").path("coordinate").asText();
        assertThat(coordinate).isEqualTo("com.sky.it:frontend-helper:1.0.0");
        verifyPageTotal(token, "/plugins/dependencies/local-artifacts?page=1&size=1", 1);
        JsonNode installed = post(token, "/plugins/definitions/" + PLUGIN_ID + "/dependencies/install", Map.of(
                "dependencies", List.of(coordinate),
                "mode", "replace"
        )).path("data");
        assertThat(installed.path("pluginId").asText()).isEqualTo(PLUGIN_ID);
        assertThat(installed.path("resolvedDependencyCount").asInt()).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM plugin_installation WHERE plugin_id=?", Integer.class, PLUGIN_ID)).isZero();
    }

    private String createInstallation(String token, String name) throws Exception {
        return createInstallation(token, PLUGIN_ID, name);
    }

    private String createInstallation(String token, String pluginId, String name) throws Exception {
        return post(token, "/plugins/installations", Map.of("pluginId", pluginId, "installationName", name))
                .path("data").path("installationId").asText();
    }

    private void createNotificationChannels(String token) throws Exception {
        createChannel(token, CHANNEL_A, "Frontend IT Channel A");
        createChannel(token, CHANNEL_B, "Frontend IT Channel B");
        verifyPageTotal(token, "/notifications/channels?page=1&size=1", 2);
    }

    private void createChannel(String token, String channelId, String displayName) throws Exception {
        request(HttpMethod.POST, token, "/notifications/channels", Map.of(
                "channelId", channelId,
                "channelType", "WEBHOOK",
                "displayName", displayName,
                "enabled", false,
                "configJson", "{\"url\":\"http://127.0.0.1:9/frontend-it\"}",
                "remark", "frontend integration fixture"
        ), HttpStatus.OK);
    }

    private void replaceInstallationChannels(String token, String installationId, List<String> channelIds) throws Exception {
        JsonNode response = put(token, "/plugins/installations/" + installationId + "/notification-channels", Map.of("channelIds", channelIds));
        assertThat(response.path("data").size()).isEqualTo(channelIds.size());
    }

    private void verifyInstallationConfigAccountSchemaAndLifecycleIsolation(String token, String installationA, String installationB) throws Exception {
        put(token, "/plugins/installations/" + installationA + "/config", Map.of("configJson", "{\"message\":\"config-a\"}"));
        put(token, "/plugins/installations/" + installationB + "/config", Map.of("configJson", "{\"message\":\"config-b\"}"));
        assertThat(get(token, "/plugins/installations/" + installationA + "/config").path("data").path("configJson").asText()).contains("config-a");
        assertThat(get(token, "/plugins/installations/" + installationB + "/config").path("data").path("configJson").asText()).contains("config-b");

        post(token, "/plugins/installations/" + installationA + "/accounts", Map.of(
                "accountId", "account-a",
                "displayName", "Account A",
                "accountJson", "{\"accountId\":\"account-a\",\"secret\":\"secret-a\",\"region\":\"east\",\"expireAt\":\"2026-12-31T23:59:59\"}",
                "enabled", true
        ));
        post(token, "/plugins/installations/" + installationB + "/accounts", Map.of(
                "accountId", "account-b",
                "displayName", "Account B",
                "accountJson", "{\"accountId\":\"account-b\",\"secret\":\"secret-b\",\"region\":\"west\",\"expireAt\":\"2027-01-31T23:59:59\"}",
                "enabled", true
        ));
        JsonNode accountsA = get(token, "/plugins/installations/" + installationA + "/accounts?page=1&size=20").path("data");
        JsonNode accountsB = get(token, "/plugins/installations/" + installationB + "/accounts?page=1&size=20").path("data");
        assertThat(accountsA.path("total").asLong()).isEqualTo(1);
        assertThat(accountsA.path("items").get(0).path("accountId").asText()).isEqualTo("account-a");
        assertThat(accountsA.path("items").get(0).has("accountJson")).isFalse();
        assertThat(accountsA.path("items").get(0).path("displayValues").isEmpty()).isTrue();
        assertThat(accountsB.path("total").asLong()).isEqualTo(1);
        assertThat(accountsB.path("items").get(0).path("accountId").asText()).isEqualTo("account-b");

        JsonNode accountDetail = get(token, "/plugins/installations/" + installationA + "/accounts/account-a").path("data");
        assertThat(accountDetail.path("accountJson").asText()).contains("secret-a");

        JsonNode defaultPreference = get(token, "/plugins/installations/" + installationA + "/accounts/view-preference").path("data");
        assertThat(defaultPreference.path("fields").isEmpty()).isTrue();
        assertThat(defaultPreference.path("maxFields").asInt()).isEqualTo(5);
        assertThat(defaultPreference.path("availableFields").findValuesAsText("name"))
                .containsExactly("region", "expireAt");

        JsonNode preference = put(token, "/plugins/installations/" + installationA + "/accounts/view-preference", Map.of(
                "fields", List.of("expireAt", "region")
        )).path("data");
        assertThat(preference.path("fields")).extracting(JsonNode::asText)
                .containsExactly("expireAt", "region");

        JsonNode projectedAccount = get(token, "/plugins/installations/" + installationA + "/accounts?page=1&size=20")
                .path("data").path("items").get(0);
        assertThat(projectedAccount.has("accountJson")).isFalse();
        assertThat(projectedAccount.path("displayValues").path("expireAt").asText()).isEqualTo("2026-12-31T23:59:59");
        assertThat(projectedAccount.path("displayValues").path("region").asText()).isEqualTo("east");
        assertThat(projectedAccount.toString()).doesNotContain("secret-a");

        String schemaA = get(token, "/plugins/installations/" + installationA + "/accounts/schema").path("data").path("schemaJson").asText();
        String schemaB = get(token, "/plugins/installations/" + installationB + "/accounts/schema").path("data").path("schemaJson").asText();
        assertThat(schemaA).isNotBlank().isEqualTo(schemaB);

        put(token, "/plugins/installations/" + installationA + "/debug", Map.of("enabled", false, "port", 5005, "suspend", false));
        put(token, "/plugins/installations/" + installationB + "/debug", Map.of("enabled", false, "port", 5006, "suspend", false));
        assertThat(get(token, "/plugins/installations/" + installationA).path("data").path("remoteDebugPort").asInt()).isEqualTo(5005);
        assertThat(get(token, "/plugins/installations/" + installationB).path("data").path("remoteDebugPort").asInt()).isEqualTo(5006);

        JsonNode startedA = post(token, "/plugins/installations/" + installationA + "/start", Map.of()).path("data");
        assertThat(startedA.path("state").asText()).isEqualTo("RUNNING");
        assertThat(get(token, "/plugins/installations/" + installationB).path("data").path("state").asText()).isEqualTo("STOPPED");
        post(token, "/plugins/installations/" + installationA + "/stop", Map.of());

        JsonNode auditA = get(token, "/plugins/audit-logs?installationId=" + installationA + "&page=1&size=100").path("data");
        assertThat(auditA.path("total").asLong()).isPositive();
        for (JsonNode item : auditA.path("items")) {
            assertThat(item.path("installationId").asText()).isEqualTo(installationA);
        }
    }

    private long verifyJobChannelAndRuleContracts(String token, String installationA, String installationB) throws Exception {
        Map<String, Object> create = jobBody(installationA, List.of(CHANNEL_A));
        JsonNode job = post(token, "/jobs", create).path("data");
        long jobId = job.path("id").asLong();
        assertThat(job.path("pluginInstallationId").asText()).isEqualTo(installationA);
        assertThat(job.path("notificationChannelIds").toString()).contains(CHANNEL_A);

        expectError(HttpMethod.PUT, token, "/jobs/" + jobId, jobBody(installationB, null), HttpStatus.BAD_REQUEST, 400001, "outside the plugin installation channel pool");
        put(token, "/jobs/" + jobId + "/notification-channels", Map.of("channelIds", List.of()));
        JsonNode switched = put(token, "/jobs/" + jobId, jobBody(installationB, null)).path("data");
        assertThat(switched.path("pluginInstallationId").asText()).isEqualTo(installationB);
        put(token, "/jobs/" + jobId + "/notification-channels", Map.of("channelIds", List.of(CHANNEL_B)));

        request(HttpMethod.POST, token, "/notifications/rules", Map.of(
                "ruleId", RULE_ID,
                "displayName", "Frontend IT job success",
                "enabled", true,
                "eventType", "JOB_EXECUTION_SUCCESS",
                "pluginInstallationId", installationB,
                "jobId", Long.toString(jobId),
                "severity", "INFO",
                "channelIds", List.of(CHANNEL_B),
                "remark", "frontend integration fixture"
        ), HttpStatus.OK);
        JsonNode rule = get(token, "/notifications/rules/" + RULE_ID).path("data");
        assertThat(rule.path("jobId").asText()).isEqualTo(Long.toString(jobId));
        assertThat(rule.path("pluginInstallationId").asText()).isEqualTo(installationB);
        return jobId;
    }

    private void verifyServicePermissionCrud(String token, String callerInstallationId, String targetInstallationId) throws Exception {
        long permissionId = createServicePermission(token, callerInstallationId, targetInstallationId);
        JsonNode page = get(token, "/plugin-service/permissions?callerInstallationId=" + callerInstallationId
                + "&targetInstallationId=" + targetInstallationId + "&page=1&size=1").path("data");
        assertThat(page.path("total").asLong()).isEqualTo(1);
        assertThat(page.path("items").get(0).path("id").asLong()).isEqualTo(permissionId);

        JsonNode detail = get(token, "/plugin-service/permissions/" + permissionId).path("data");
        assertThat(detail.path("targetPluginId").asText()).isEqualTo(SERVICE_PLUGIN_ID);
        assertThat(detail.path("operation").asText()).isEqualTo(SERVICE_OPERATION);

        JsonNode disabled = request(HttpMethod.PATCH, token, "/plugin-service/permissions/" + permissionId + "/enabled",
                Map.of("enabled", false), HttpStatus.OK).path("data");
        assertThat(disabled.path("enabled").asBoolean()).isFalse();
        JsonNode enabled = request(HttpMethod.PATCH, token, "/plugin-service/permissions/" + permissionId + "/enabled",
                Map.of("enabled", true), HttpStatus.OK).path("data");
        assertThat(enabled.path("enabled").asBoolean()).isTrue();

        JsonNode deleted = request(HttpMethod.DELETE, token, "/plugin-service/permissions/" + permissionId, null, HttpStatus.OK).path("data");
        assertThat(deleted.path("success").asBoolean()).isTrue();
        assertThat(get(token, "/plugin-service/permissions?callerInstallationId=" + callerInstallationId
                + "&targetInstallationId=" + targetInstallationId + "&page=1&size=1")
                .path("data").path("total").asLong()).isZero();
    }

    private long createServicePermission(String token, String callerInstallationId, String targetInstallationId) throws Exception {
        JsonNode permission = post(token, "/plugin-service/permissions", Map.of(
                "callerInstallationId", callerInstallationId,
                "targetInstallationId", targetInstallationId,
                "operation", SERVICE_OPERATION,
                "enabled", true
        )).path("data");
        assertThat(permission.path("callerInstallationId").asText()).isEqualTo(callerInstallationId);
        assertThat(permission.path("targetInstallationId").asText()).isEqualTo(targetInstallationId);
        assertThat(permission.path("targetPluginId").asText()).isEqualTo(SERVICE_PLUGIN_ID);
        assertThat(permission.path("enabled").asBoolean()).isTrue();
        return permission.path("id").asLong();
    }

    private void verifyServiceCallLog(
            String token,
            long jobId,
            long executionId,
            String callerInstallationId,
            String targetInstallationId,
            long permissionId
    ) throws Exception {
        JsonNode permissionPage = get(token, "/plugin-service/permissions?callerInstallationId=" + callerInstallationId
                + "&targetInstallationId=" + targetInstallationId + "&operation=" + SERVICE_OPERATION + "&page=1&size=1").path("data");
        assertThat(permissionPage.path("total").asLong()).isEqualTo(1);
        assertThat(permissionPage.path("items").get(0).path("id").asLong()).isEqualTo(permissionId);

        JsonNode page = get(token, "/plugin-service/calls?jobId=" + jobId + "&callerInstallationId=" + callerInstallationId
                + "&targetInstallationId=" + targetInstallationId + "&operation=" + SERVICE_OPERATION + "&page=1&size=1").path("data");
        assertThat(page.path("total").asLong()).isEqualTo(1);
        JsonNode item = page.path("items").get(0);
        assertThat(item.path("executionId").asLong()).isEqualTo(executionId);
        assertThat(item.path("callerInstallationId").asText()).isEqualTo(callerInstallationId);
        assertThat(item.path("targetInstallationId").asText()).isEqualTo(targetInstallationId);
        assertThat(item.path("targetPluginId").asText()).isEqualTo(SERVICE_PLUGIN_ID);
        assertThat(item.path("operation").asText()).isEqualTo(SERVICE_OPERATION);
        assertThat(item.path("success").asBoolean()).isTrue();

        long callId = item.path("id").asLong();
        JsonNode detail = get(token, "/plugin-service/calls/" + callId).path("data");
        assertThat(detail.path("serviceRequestId").asText()).isNotBlank();
        assertThat(detail.path("responseBytes").asInt()).isPositive();
        JsonNode runnerLog = get(token, "/plugin-service/calls/" + callId + "/logs?tailBytes=65536").path("data");
        assertThat(runnerLog.path("exists").asBoolean()).isTrue();
        assertThat(runnerLog.path("targetInstallationId").asText()).isEqualTo(targetInstallationId);
        assertThat(runnerLog.path("content").asText()).contains("frontend integration service");
    }

    private Map<String, Object> jobBody(String installationId, List<String> notificationChannelIds) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("jobName", "Frontend IT Job");
        body.put("pluginInstallationId", installationId);
        body.put("cronExpression", "0 0 0 1 1 ? 2099");
        body.put("misfirePolicy", "DO_NOTHING");
        body.put("concurrent", false);
        body.put("inputJson", "{\"push\":true}");
        body.put("timeoutMillis", 30_000);
        body.put("remark", "frontend integration fixture");
        if (notificationChannelIds != null) {
            body.put("notificationChannelIds", notificationChannelIds);
        }
        return body;
    }

    private long runAndAwait(String token, long jobId, Map<String, Object> manualRun, String expectedStatus) throws Exception {
        long executionId = post(token, "/jobs/" + jobId + "/run", manualRun).path("data").path("executionId").asLong();
        long deadline = System.nanoTime() + Duration.ofSeconds(45).toNanos();
        while (System.nanoTime() < deadline) {
            JsonNode execution = get(token, "/job-executions/" + executionId).path("data");
            String status = execution.path("status").asText();
            if (!"RUNNING".equals(status)) {
                assertThat(status).isEqualTo(expectedStatus);
                return executionId;
            }
            Thread.sleep(200);
        }
        throw new AssertionError("Timed out waiting for execution " + executionId);
    }

    private void verifyExecutionLogIsolation(String token, long executionId, String installationId) throws Exception {
        JsonNode log = get(token, "/job-executions/" + executionId + "/logs?tailBytes=65536").path("data");
        assertThat(log.path("pluginInstallationId").asText()).isEqualTo(installationId);
        assertThat(log.path("exists").asBoolean()).isTrue();
        assertThat(log.path("content").asText()).contains("frontend integration task");
    }

    private JsonNode waitForHistory(String token, long jobId) throws Exception {
        long deadline = System.nanoTime() + Duration.ofSeconds(10).toNanos();
        while (System.nanoTime() < deadline) {
            JsonNode page = get(token, "/notifications/history?jobId=" + jobId + "&page=1&size=20").path("data");
            if (page.path("total").asLong() > 0) {
                return page.path("items").get(0);
            }
            Thread.sleep(100);
        }
        throw new AssertionError("Timed out waiting for notification history");
    }

    private TargetFixture createSelfScopedTargetUser(String adminToken, long departmentId) throws Exception {
        long roleId = post(adminToken, "/system/roles", Map.of(
                "roleName", "FRONTEND_IT_SELF",
                "description", "Frontend integration self-scoped role"
        )).path("data").path("roleId").asLong();
        put(adminToken, "/system/roles/" + roleId + "/data-scope", Map.of("dataScope", "SELF", "departmentIds", List.of()));
        Set<String> permissions = Set.of(
                "user:read:self",
                "plugin:runtime:read", "plugin:runtime:manage",
                "plugin:config:read", "plugin:config:write",
                "plugin:account:read", "plugin:account:write",
                "plugin:audit:read", "plugin:notification-channel:bind",
                "notification:channel:read", "notification:channel:write",
                "notification:rule:read", "notification:rule:write",
                "notification:history:read",
                "job:read", "job:write", "job:run", "job:execution:read", "job:notification:bind"
        );
        put(adminToken, "/system/roles/" + roleId + "/permissions", Map.of("permissions", permissions));
        JsonNode user = post(adminToken, "/system/users", Map.of(
                "username", "frontend_it_target",
                "password", ADMIN_PASSWORD,
                "email", "frontend-it-target@example.test",
                "departmentId", departmentId,
                "enabled", true,
                "roles", List.of("FRONTEND_IT_SELF")
        )).path("data");
        String token = login("frontend_it_target", ADMIN_PASSWORD);
        return new TargetFixture(user.path("userId").asText(), token);
    }

    private void verifyPageTotal(String token, String path, long minimumTotal) throws Exception {
        JsonNode page = get(token, path).path("data");
        assertThat(page.path("items").isArray()).isTrue();
        assertThat(page.path("page").asInt()).isPositive();
        assertThat(page.path("size").asInt()).isPositive();
        assertThat(page.path("total").asLong()).isGreaterThanOrEqualTo(minimumTotal);
    }

    private void assertPageTotal(String token, String path, long total) throws Exception {
        assertThat(get(token, path).path("data").path("total").asLong()).isEqualTo(total);
    }

    private void verifyManualSeedIsIdempotent() {
        Path seed = DatabaseInitializer.findWorkspaceFile("sql/frontend-integration-seed-mysql.sql");
        ResourceDatabasePopulator populator = new ResourceDatabasePopulator(new FileSystemResource(seed));
        populator.execute(dataSource);
        populator.execute(dataSource);
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM sky_department WHERE department_id IN (910001, 910002)", Integer.class)).isEqualTo(2);
        assertThat(jdbcTemplate.queryForObject("SELECT enabled FROM sky_department WHERE department_id=910002", Boolean.class)).isFalse();
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM sky_role r JOIN sky_role_department rd ON rd.role_id=r.role_id WHERE r.role_name='FRONTEND_IT_SEED_CUSTOM' AND rd.department_id=910002", Integer.class)).isEqualTo(1);
    }

    private JsonNode get(String token, String path) throws Exception {
        return request(HttpMethod.GET, token, path, null, HttpStatus.OK);
    }

    private JsonNode post(String token, String path, Object body) throws Exception {
        return request(HttpMethod.POST, token, path, body, HttpStatus.OK);
    }

    private JsonNode put(String token, String path, Object body) throws Exception {
        return request(HttpMethod.PUT, token, path, body, HttpStatus.OK);
    }

    private JsonNode request(HttpMethod method, String token, String path, Object body, HttpStatus expectedStatus) throws Exception {
        HttpHeaders headers = headers(token);
        headers.setContentType(MediaType.APPLICATION_JSON);
        String payload = body == null ? null : objectMapper.writeValueAsString(body);
        ResponseEntity<String> response = restTemplate.exchange(url(path), method, new HttpEntity<>(payload, headers), String.class);
        assertThat(response.getStatusCode()).as(method + " " + path + " response=" + response.getBody()).isEqualTo(expectedStatus);
        if (response.getBody() == null || response.getBody().isBlank()) {
            return objectMapper.createObjectNode().put("code", 0).putNull("data");
        }
        JsonNode root = objectMapper.readTree(response.getBody());
        if (expectedStatus.is2xxSuccessful()) {
            assertThat(root.path("code").asInt()).as(response.getBody()).isZero();
        }
        return root;
    }

    private JsonNode multipart(String token, String path, Map<String, Object> parts) throws Exception {
        MultiValueMap<String, Object> form = new LinkedMultiValueMap<>();
        parts.forEach(form::add);
        HttpHeaders headers = headers(token);
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);
        ResponseEntity<String> response = restTemplate.exchange(url(path), HttpMethod.POST, new HttpEntity<>(form, headers), String.class);
        assertThat(response.getStatusCode()).as(path + " response=" + response.getBody()).isEqualTo(HttpStatus.OK);
        JsonNode root = objectMapper.readTree(response.getBody());
        assertThat(root.path("code").asInt()).isZero();
        return root;
    }

    private void expectError(HttpMethod method, String token, String path, Object body, HttpStatus status, int code, String messagePart) throws Exception {
        JsonNode root = request(method, token, path, body, status);
        assertThat(root.path("code").asInt()).isEqualTo(code);
        assertThat(root.path("msg").asText()).contains(messagePart);
    }

    private HttpHeaders headers(String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setAccept(List.of(MediaType.APPLICATION_JSON));
        if (token != null && !token.isBlank()) {
            headers.setBearerAuth(token);
        }
        return headers;
    }

    private String url(String path) {
        return "http://127.0.0.1:" + port + "/api" + path;
    }

    private JsonNode findDepartment(JsonNode nodes, long departmentId) {
        if (nodes == null || !nodes.isArray()) return null;
        for (JsonNode node : nodes) {
            if (node.path("departmentId").asLong() == departmentId) return node;
            JsonNode nested = findDepartment(node.path("children"), departmentId);
            if (nested != null) return nested;
        }
        return null;
    }

    private Path requiredFile(String property) {
        Path path = Path.of(System.getProperty(property, "")).toAbsolutePath().normalize();
        assertThat(path).as(property).isRegularFile();
        return path;
    }

    record DepartmentFixture(long enabledDepartmentId, long disabledDepartmentId) {
    }

    record TargetFixture(String userId, String token) {
    }

    static class DatabaseInitializer implements ApplicationContextInitializer<ConfigurableApplicationContext> {
        @Override
        public void initialize(ConfigurableApplicationContext context) {
            if (!Boolean.parseBoolean(System.getProperty("sky.it.allow-reset", "false"))) {
                throw new IllegalStateException("Explicit sky.it.allow-reset=true is required");
            }
            String databaseUrl = System.getProperty("sky.it.db.url", "");
            if (!databaseUrl.matches("^jdbc:mysql://[^/]+/" + DATABASE_NAME + "(?:\\?.*)?$")) {
                throw new IllegalStateException("Integration JDBC URL must target exactly " + DATABASE_NAME);
            }
            String username = System.getProperty("sky.it.db.username", "root");
            String password = System.getProperty("sky.it.db.password", "root");
            String serverUrl = databaseUrl.replaceFirst("/" + DATABASE_NAME + "(?=\\?|$)", "/");
            resetDatabase(serverUrl, databaseUrl, username, password);

            int grpcPort = freePort();
            Path workspace = Path.of(System.getProperty("sky.it.workspace")).toAbsolutePath().normalize();
            Path runnerJar = Path.of(System.getProperty("sky.it.runner.jar")).toAbsolutePath().normalize();
            TestPropertyValues.of(
                    "spring.datasource.url=" + databaseUrl,
                    "spring.datasource.username=" + username,
                    "spring.datasource.password=" + password,
                    "spring.quartz.auto-startup=false",
                    "sky.retention.scheduled-enabled=false",
                    "sky.notification.retry-enabled=false",
                    "sky.plugin.host-grpc.port=" + grpcPort,
                    "sky.plugin.storage-root=" + propertyPath(workspace.resolve("storage")),
                    "sky.plugin.artifact-root=" + propertyPath(workspace.resolve("artifacts")),
                    "sky.plugin.upload.artifact-root=" + propertyPath(workspace.resolve("artifacts")),
                    "sky.plugin.dependencies.repository-root=" + propertyPath(workspace.resolve("maven-repository")),
                    "sky.plugin.runtime.process.runner-jar=" + propertyPath(runnerJar),
                    "sky.plugin.runtime.process.artifact-root=" + propertyPath(workspace.resolve("artifacts")),
                    "sky.plugin.runtime.process.work-root=" + propertyPath(workspace.resolve("runtime")),
                    "sky.plugin.runtime.process.log-root=" + propertyPath(workspace.resolve("logs")),
                    "sky.plugin.runtime.process.startup-timeout=20s",
                    "sky.plugin.runtime.process.task-timeout=45s"
            ).applyTo(context);
        }

        private static void resetDatabase(String serverUrl, String databaseUrl, String username, String password) {
            try {
                Class.forName("com.mysql.cj.jdbc.Driver");
                try (var connection = DriverManager.getConnection(serverUrl, username, password); Statement statement = connection.createStatement()) {
                    statement.execute("DROP DATABASE IF EXISTS " + DATABASE_NAME);
                    statement.execute("CREATE DATABASE " + DATABASE_NAME + " DEFAULT CHARACTER SET utf8mb4 DEFAULT COLLATE utf8mb4_0900_ai_ci");
                }
                Path schema = findWorkspaceFile("sql/schema-mysql.sql");
                String sql = Files.readString(schema, StandardCharsets.UTF_8)
                        .replace("CREATE DATABASE IF NOT EXISTS sky_new", "CREATE DATABASE IF NOT EXISTS " + DATABASE_NAME)
                        .replace("USE sky_new;", "USE " + DATABASE_NAME + ";");
                ResourceDatabasePopulator populator = new ResourceDatabasePopulator(new ByteArrayResource(sql.getBytes(StandardCharsets.UTF_8)));
                populator.setContinueOnError(false);
                try (var connection = DriverManager.getConnection(databaseUrl, username, password)) {
                    populator.populate(connection);
                    verifyAccountGroupUpgrade(connection);
                }
            } catch (Exception exception) {
                throw new IllegalStateException("Failed to reset dedicated integration database " + DATABASE_NAME, exception);
            }
        }

        private static void verifyAccountGroupUpgrade(java.sql.Connection connection) throws Exception {
            // Exercise the shipped fresh schema, then reproduce the pre-group schema in this disposable database.
            try (Statement statement = connection.createStatement()) {
                statement.execute("ALTER TABLE plugin_account DROP FOREIGN KEY fk_plugin_account_group, DROP INDEX idx_plugin_account_created, DROP INDEX idx_plugin_account_group_created, DROP COLUMN group_id");
                statement.execute("DROP TABLE plugin_account_group");
                statement.execute("INSERT INTO sky_user (user_id,username,password_hash,email) VALUES ('migration-user','migration-user','unused','migration@example.test')");
                statement.execute("INSERT INTO plugin_definition (plugin_id,capabilities_json,config_schema_json,account_schema_json,dependency_jars_json) VALUES ('migration-plugin','{}','{}','{}','[]')");
                statement.execute("INSERT INTO plugin_installation (installation_id,plugin_id,instance_id,owner_user_id,owner_dept_id) VALUES ('migration-installation','migration-plugin','migration-instance','migration-user',1)");
                statement.execute("INSERT INTO plugin_account (installation_id,account_id,account_json,enabled,created_at,updated_at) VALUES ('migration-installation','legacy-account','{\"marker\":\"preserved\"}',false,'2026-01-01 00:00:00','2026-01-02 00:00:00')");
            }
            String upgrade = Files.readString(findWorkspaceFile("sql/add-plugin-account-groups-mysql.sql"), StandardCharsets.UTF_8)
                    .replace("USE sky_new;", "USE " + DATABASE_NAME + ";");
            ResourceDatabasePopulator migration = new ResourceDatabasePopulator(new ByteArrayResource(upgrade.getBytes(StandardCharsets.UTF_8)));
            migration.populate(connection);
            try (Statement statement = connection.createStatement()) {
                try (var row = statement.executeQuery("SELECT * FROM plugin_account WHERE account_id='legacy-account' AND installation_id='migration-installation'")) {
                    assertThat(row.next()).isTrue();
                    assertThat(row.getObject("group_id")).isNull();
                    assertThat(row.getString("account_json")).contains("preserved");
                    assertThat(row.getBoolean("enabled")).isFalse();
                    assertThat(row.getTimestamp("created_at").toLocalDateTime()).isEqualTo(java.time.LocalDateTime.of(2026, 1, 1, 0, 0));
                    assertThat(row.getTimestamp("updated_at").toLocalDateTime()).isEqualTo(java.time.LocalDateTime.of(2026, 1, 2, 0, 0));
                }
                statement.execute("DELETE FROM plugin_account WHERE installation_id='migration-installation'");
                statement.execute("DELETE FROM plugin_installation WHERE installation_id='migration-installation'");
                statement.execute("DELETE FROM plugin_definition WHERE plugin_id='migration-plugin'");
                statement.execute("DELETE FROM sky_user WHERE user_id='migration-user'");
            }
        }

        private static Path findWorkspaceFile(String relativePath) {
            Path current = Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize();
            while (current != null) {
                Path candidate = current.resolve(relativePath);
                if (Files.isRegularFile(candidate)) return candidate;
                current = current.getParent();
            }
            throw new IllegalStateException(relativePath + " not found");
        }

        private static int freePort() {
            try (ServerSocket socket = new ServerSocket(0)) {
                return socket.getLocalPort();
            } catch (Exception exception) {
                throw new IllegalStateException("Failed to allocate integration gRPC port", exception);
            }
        }

        private static String propertyPath(Path path) {
            return path.toAbsolutePath().normalize().toString().replace('\\', '/');
        }
    }
}
