package com.sky.job.plugin.management;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sky.job.plugin.management.dto.BatchDeletePluginAccountRequest;
import com.sky.job.plugin.management.dto.BatchDeletePluginAccountResponse;
import com.sky.job.plugin.management.dto.PluginAccountExportFile;
import com.sky.job.plugin.management.dto.PluginAccountImportResponse;
import com.sky.job.plugin.runtime.PluginRuntimeRecord;
import com.sky.job.plugin.runtime.PluginRuntimeState;
import com.sky.job.plugin.security.PluginCapability;
import com.sky.job.plugin.storage.PluginAccount;
import com.sky.job.plugin.storage.PluginAccountStore;
import com.sky.job.plugin.storage.PluginAccountGroupStore;
import com.sky.job.plugin.storage.PluginAuditStore;
import com.sky.job.plugin.storage.PluginConfigStore;
import com.sky.job.plugin.storage.PluginPayloadValidator;
import com.sky.job.scheduler.PluginRuntimeMetadataStore;
import com.sky.job.security.AuthenticatedUser;
import com.sky.job.security.DataScopeService;
import com.sky.job.security.EffectiveDataScope;
import com.sky.job.security.PermissionNames;
import com.sky.job.system.DynamicPluginStorageSettings;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PluginManagementServiceTest {
    private static final String INSTALLATION_ID = "installation-a";
    private static final List<String> ACCOUNT_FILE_HEADERS = List.of(
            "phone",
            "nickname",
            "tags",
            "excludedGoodsNames",
            "attributes",
            "credentials",
            "enabled"
    );
    private static final String COMPLEX_ACCOUNT_SCHEMA = """
            {
              "accountIdField": "phone",
              "fields": [
                {"name":"phone","label":"Phone","type":"TEXT","sensitive":false},
                {"name":"nickname","label":"Nickname","type":"TEXT","sensitive":false},
                {"name":"tags","label":"Tags","type":"MULTI_SELECT","sensitive":false},
                {"name":"excludedGoodsNames","label":"Excluded goods","type":"LIST","sensitive":false},
                {"name":"attributes","label":"Attributes","type":"MAP","sensitive":false},
                {"name":"credentials","label":"Credentials","type":"OBJECT_LIST","sensitive":false}
              ]
            }
            """;

    @Mock private PluginRuntimeMetadataStore runtimeStore;
    @Mock private PluginConfigStore configStore;
    @Mock private PluginAccountStore accountStore;
    @Mock private PluginAccountGroupStore accountGroupStore;
    @Mock private PluginAuditStore auditStore;
    @Mock private DataScopeService dataScopeService;
    @Mock private PluginAccountListViewService accountListViewService;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private AuthenticatedUser user;
    private String accountSchemaJson;

    @BeforeEach
    void setUp() {
        user = new AuthenticatedUser("user-1", "alice", "", 1L, true, Set.of("ADMIN"), Set.of());
        accountSchemaJson = "{}";
        EffectiveDataScope scope = EffectiveDataScope.all(user.userId());
        when(runtimeStore.findRuntimeByInstallationId(INSTALLATION_ID)).thenAnswer(invocation -> Optional.of(runtime()));
        when(runtimeStore.runtimeCapabilities("{\"account\":true}")).thenReturn(Set.of(PluginCapability.ACCOUNT));
        when(dataScopeService.resolve(user, PermissionNames.PLUGIN_ACCOUNT_WRITE)).thenReturn(scope);
        when(dataScopeService.allows(scope, "user-1", 1L)).thenReturn(true);
    }

    @ParameterizedTest
    @ValueSource(strings = {"csv", "xlsx"})
    void importsComplexFieldsAsJsonContainers(String format) throws Exception {
        accountSchemaJson = COMPLEX_ACCOUNT_SCHEMA;
        when(accountStore.getManaged(INSTALLATION_ID, "13800138000")).thenReturn(Optional.empty());
        MockMultipartFile file = accountFile(format, ACCOUNT_FILE_HEADERS, List.of(List.of(
                "13800138000",
                "Alice",
                "[\"primary\",\"backup\"]",
                "[\"商品A\",\"商品B\"]",
                "{\"tier\":\"gold\",\"count\":2}",
                "[{\"name\":\"main\",\"token\":\"secret\"}]",
                "false"
        )));

        PluginAccountImportResponse response = service().importAccounts(INSTALLATION_ID, file, false, true, user);

        assertThat(response.successRows()).isEqualTo(1);
        assertThat(response.failedRows()).isZero();
        assertThat(response.createdRows()).isEqualTo(1);
        ArgumentCaptor<String> accountJsonCaptor = ArgumentCaptor.forClass(String.class);
        verify(accountStore).upsert(
                eq(INSTALLATION_ID),
                eq("13800138000"),
                eq("13800138000"),
                accountJsonCaptor.capture(),
                eq(false)
        );
        JsonNode accountJson = objectMapper.readTree(accountJsonCaptor.getValue());
        assertThat(accountJson.path("phone").asText()).isEqualTo("13800138000");
        assertThat(accountJson.path("nickname").asText()).isEqualTo("Alice");
        assertThat(accountJson.path("tags")).isEqualTo(objectMapper.readTree("[\"primary\",\"backup\"]"));
        assertThat(accountJson.path("excludedGoodsNames")).isEqualTo(objectMapper.readTree("[\"商品A\",\"商品B\"]"));
        assertThat(accountJson.path("attributes")).isEqualTo(objectMapper.readTree("{\"tier\":\"gold\",\"count\":2}"));
        assertThat(accountJson.path("credentials")).isEqualTo(objectMapper.readTree("[{\"name\":\"main\",\"token\":\"secret\"}]"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"csv", "xlsx"})
    void importsBlankComplexFieldsAsEmptyContainers(String format) throws Exception {
        accountSchemaJson = COMPLEX_ACCOUNT_SCHEMA;
        when(accountStore.getManaged(INSTALLATION_ID, "13800138001")).thenReturn(Optional.empty());
        MockMultipartFile file = accountFile(format, ACCOUNT_FILE_HEADERS, List.of(List.of(
                "13800138001", "", "", "", "", "", ""
        )));

        PluginAccountImportResponse response = service().importAccounts(INSTALLATION_ID, file, false, false, user);

        assertThat(response.successRows()).isEqualTo(1);
        assertThat(response.failedRows()).isZero();
        ArgumentCaptor<String> accountJsonCaptor = ArgumentCaptor.forClass(String.class);
        verify(accountStore).upsert(
                eq(INSTALLATION_ID),
                eq("13800138001"),
                eq("13800138001"),
                accountJsonCaptor.capture(),
                eq(false)
        );
        JsonNode accountJson = objectMapper.readTree(accountJsonCaptor.getValue());
        assertThat(accountJson.path("nickname").isTextual()).isTrue();
        assertThat(accountJson.path("nickname").asText()).isEmpty();
        assertThat(accountJson.path("tags")).isEqualTo(objectMapper.readTree("[]"));
        assertThat(accountJson.path("excludedGoodsNames")).isEqualTo(objectMapper.readTree("[]"));
        assertThat(accountJson.path("attributes")).isEqualTo(objectMapper.readTree("{}"));
        assertThat(accountJson.path("credentials")).isEqualTo(objectMapper.readTree("[]"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"csv", "xlsx"})
    void rejectsInvalidOrMismatchedComplexJsonPerRow(String format) throws Exception {
        accountSchemaJson = COMPLEX_ACCOUNT_SCHEMA;
        MockMultipartFile file = accountFile(format, ACCOUNT_FILE_HEADERS, List.of(
                List.of("invalid-json", "", "[]", "not-json", "{}", "[]", "true"),
                List.of("wrong-array", "", "[]", "{}", "{}", "[]", "true"),
                List.of("wrong-object", "", "[]", "[]", "[]", "[]", "true")
        ));

        PluginAccountImportResponse response = service().importAccounts(INSTALLATION_ID, file, false, true, user);

        assertThat(response.totalRows()).isEqualTo(3);
        assertThat(response.successRows()).isZero();
        assertThat(response.failedRows()).isEqualTo(3);
        assertThat(response.rows())
                .extracting(row -> row.accountId(), row -> row.status(), row -> row.message())
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple(
                                "invalid-json", "FAILED", "Invalid JSON for account field 'excludedGoodsNames': expected array"),
                        org.assertj.core.groups.Tuple.tuple(
                                "wrong-array", "FAILED", "Invalid JSON for account field 'excludedGoodsNames': expected array"),
                        org.assertj.core.groups.Tuple.tuple(
                                "wrong-object", "FAILED", "Invalid JSON for account field 'attributes': expected object")
                );
        verifyNoInteractions(accountStore);
    }

    @ParameterizedTest
    @ValueSource(strings = {"csv", "xlsx"})
    void roundTripsComplexFieldsThroughExportAndImport(String format) throws Exception {
        accountSchemaJson = COMPLEX_ACCOUNT_SCHEMA;
        allowReadAccess();
        String originalAccountJson = """
                {
                  "phone":"13800138002",
                  "nickname":"Round trip",
                  "tags":["primary","backup"],
                  "excludedGoodsNames":["商品A","商品B"],
                  "attributes":{"tier":"gold","count":2},
                  "credentials":[{"name":"main","token":"secret"}]
                }
                """;
        LocalDateTime now = LocalDateTime.of(2026, 8, 14, 11, 0);
        PluginAccount account = new PluginAccount(
                1L, INSTALLATION_ID, "13800138002", "Round trip", originalAccountJson, true, now, now, null, null);
        when(accountStore.listManagedForExport(eq(INSTALLATION_ID), isNull(), isNull(), isNull(), isNull(), eq(false), anyInt()))
                .thenReturn(List.of(account));
        when(accountStore.getManaged(INSTALLATION_ID, "13800138002")).thenReturn(Optional.empty());

        PluginAccountExportFile exported = service().exportAccounts(
                INSTALLATION_ID, null, null, null, format, true, user);
        MockMultipartFile importFile = new MockMultipartFile(
                "file", exported.filename(), exported.contentType(), exported.content());
        PluginAccountImportResponse response = service().importAccounts(
                INSTALLATION_ID, importFile, false, true, user);

        assertThat(response.successRows()).isEqualTo(1);
        assertThat(response.failedRows()).isZero();
        ArgumentCaptor<String> accountJsonCaptor = ArgumentCaptor.forClass(String.class);
        verify(accountStore).upsert(
                eq(INSTALLATION_ID),
                eq("13800138002"),
                eq("13800138002"),
                accountJsonCaptor.capture(),
                eq(true)
        );
        assertThat(objectMapper.readTree(accountJsonCaptor.getValue()))
                .isEqualTo(objectMapper.readTree(originalAccountJson));
    }

    @Test
    void dryRunValidatesComplexFieldsWithoutWriting() throws Exception {
        accountSchemaJson = COMPLEX_ACCOUNT_SCHEMA;
        when(accountStore.getManaged(INSTALLATION_ID, "13800138003")).thenReturn(Optional.empty());
        MockMultipartFile file = accountFile("csv", ACCOUNT_FILE_HEADERS, List.of(List.of(
                "13800138003", "Dry run", "[]", "[]", "{}", "[]", "true"
        )));

        PluginAccountImportResponse response = service().importAccounts(INSTALLATION_ID, file, true, true, user);

        assertThat(response.dryRun()).isTrue();
        assertThat(response.successRows()).isEqualTo(1);
        assertThat(response.rows().getFirst().message()).isEqualTo("Validated");
        verify(accountStore).getManaged(INSTALLATION_ID, "13800138003");
        verify(accountStore, never()).upsert(anyString(), anyString(), anyString(), anyString(), anyBoolean());
    }

    @Test
    void deletesAllValidAccountsAndWritesAggregateAudit() throws Exception {
        when(accountStore.delete(INSTALLATION_ID, "account-a")).thenReturn(1);
        when(accountStore.delete(INSTALLATION_ID, "account-b")).thenReturn(1);

        BatchDeletePluginAccountResponse response = service().deleteAccounts(
                INSTALLATION_ID,
                new BatchDeletePluginAccountRequest(List.of("account-a", "account-b")),
                user
        );

        assertThat(response.installationId()).isEqualTo(INSTALLATION_ID);
        assertThat(response.total()).isEqualTo(2);
        assertThat(response.succeeded()).isEqualTo(2);
        assertThat(response.failed()).isZero();
        assertThat(response.receipts())
                .extracting(receipt -> receipt.accountId(), receipt -> receipt.success(), receipt -> receipt.message())
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple("account-a", true, "Deleted"),
                        org.assertj.core.groups.Tuple.tuple("account-b", true, "Deleted")
                );

        ArgumentCaptor<String> detailCaptor = ArgumentCaptor.forClass(String.class);
        verify(auditStore).insert(
                eq(INSTALLATION_ID), eq("demo"), eq("demo-instance"), eq("alice"),
                eq("PLUGIN_ACCOUNT_BATCH_DELETE"), detailCaptor.capture());
        assertThat(objectMapper.readTree(detailCaptor.getValue()))
                .isEqualTo(objectMapper.readTree("""
                        {"operation":"batch_delete","capability":"ACCOUNT","total":2,"succeeded":2,"failed":0}
                        """));
        assertThat(detailCaptor.getValue()).doesNotContain("account-a", "account-b");
    }

    @Test
    void preservesOrderAndReportsPartialFailures() {
        when(accountStore.delete(INSTALLATION_ID, "account-a")).thenReturn(1);
        when(accountStore.delete(INSTALLATION_ID, "missing")).thenReturn(0);

        BatchDeletePluginAccountResponse response = service().deleteAccounts(
                INSTALLATION_ID,
                new BatchDeletePluginAccountRequest(Arrays.asList(
                        " account-a ", "missing", "", "bad id", "account-a", null
                )),
                user
        );

        assertThat(response.total()).isEqualTo(6);
        assertThat(response.succeeded()).isEqualTo(1);
        assertThat(response.failed()).isEqualTo(5);
        assertThat(response.receipts())
                .extracting(receipt -> receipt.accountId(), receipt -> receipt.success(), receipt -> receipt.message())
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple("account-a", true, "Deleted"),
                        org.assertj.core.groups.Tuple.tuple("missing", false, "Plugin account not found"),
                        org.assertj.core.groups.Tuple.tuple("", false, "Invalid account id"),
                        org.assertj.core.groups.Tuple.tuple("bad id", false, "Invalid account id"),
                        org.assertj.core.groups.Tuple.tuple("account-a", false, "Duplicate account id"),
                        org.assertj.core.groups.Tuple.tuple(null, false, "Invalid account id")
                );

        verify(accountStore).delete(INSTALLATION_ID, "account-a");
        verify(accountStore).delete(INSTALLATION_ID, "missing");
        verify(accountStore, never()).delete(INSTALLATION_ID, "bad id");
    }

    @Test
    void rejectsMissingAccountIdListBeforeDeleting() {
        assertThatThrownBy(() -> service().deleteAccounts(INSTALLATION_ID, null, user))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Account ids are required");
        assertThatThrownBy(() -> service().deleteAccounts(
                INSTALLATION_ID,
                new BatchDeletePluginAccountRequest(null),
                user
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Account ids are required");
        assertThatThrownBy(() -> service().deleteAccounts(
                INSTALLATION_ID,
                new BatchDeletePluginAccountRequest(List.of()),
                user
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Account ids are required");

        verifyNoInteractions(accountStore);
        verify(auditStore, never()).insert(anyString(), anyString(), anyString(), anyString(), anyString(), anyString());
    }

    private PluginManagementService service() {
        DynamicPluginStorageSettings settings = mock(DynamicPluginStorageSettings.class);
        lenient().when(settings.maxAccountJsonBytes()).thenReturn(1_000_000);
        return new PluginManagementService(
                runtimeStore,
                configStore,
                accountStore,
                new PluginPayloadValidator(objectMapper, settings),
                auditStore,
                objectMapper,
                dataScopeService,
                accountListViewService,
                accountGroupStore
        );
    }

    private PluginRuntimeRecord runtime() {
        LocalDateTime now = LocalDateTime.of(2026, 8, 14, 10, 0);
        return new PluginRuntimeRecord(
                1L,
                INSTALLATION_ID,
                "demo",
                "demo-instance",
                "Demo Default",
                "1.0.0",
                "Demo",
                "demo.Plugin",
                "plugin.jar",
                "sha",
                "process",
                PluginRuntimeState.STOPPED,
                "127.0.0.1",
                null,
                "",
                "{\"account\":true}",
                "{}",
                accountSchemaJson,
                "[]",
                false,
                5005,
                true,
                "user-1",
                "alice",
                1L,
                "Default",
                null,
                now,
                now
        );
    }

    private void allowReadAccess() {
        when(dataScopeService.resolve(user, PermissionNames.PLUGIN_ACCOUNT_READ))
                .thenReturn(EffectiveDataScope.all(user.userId()));
    }

    private MockMultipartFile accountFile(
            String format,
            List<String> headers,
            List<List<String>> rows
    ) throws IOException {
        return switch (format) {
            case "csv" -> csvFile(headers, rows);
            case "xlsx" -> xlsxFile(headers, rows);
            default -> throw new IllegalArgumentException("Unsupported test format: " + format);
        };
    }

    private MockMultipartFile csvFile(List<String> headers, List<List<String>> rows) throws IOException {
        StringWriter writer = new StringWriter();
        try (CSVPrinter printer = new CSVPrinter(writer, CSVFormat.DEFAULT)) {
            printer.printRecord(headers);
            for (List<String> row : rows) {
                printer.printRecord(row);
            }
        }
        return new MockMultipartFile(
                "file", "accounts.csv", "text/csv", writer.toString().getBytes(StandardCharsets.UTF_8));
    }

    private MockMultipartFile xlsxFile(List<String> headers, List<List<String>> rows) throws IOException {
        try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            var sheet = workbook.createSheet("accounts");
            var headerRow = sheet.createRow(0);
            for (int i = 0; i < headers.size(); i++) {
                headerRow.createCell(i).setCellValue(headers.get(i));
            }
            for (int i = 0; i < rows.size(); i++) {
                var row = sheet.createRow(i + 1);
                for (int j = 0; j < rows.get(i).size(); j++) {
                    row.createCell(j).setCellValue(rows.get(i).get(j));
                }
            }
            workbook.write(output);
            return new MockMultipartFile(
                    "file",
                    "accounts.xlsx",
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                    output.toByteArray()
            );
        }
    }
}
