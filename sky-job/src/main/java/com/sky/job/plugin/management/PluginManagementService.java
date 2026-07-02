package com.sky.job.plugin.management;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sky.job.plugin.management.dto.BatchPluginAccountStateReceipt;
import com.sky.job.plugin.management.dto.BatchUpdatePluginAccountStateRequest;
import com.sky.job.plugin.management.dto.BatchUpdatePluginAccountStateResponse;
import com.sky.job.plugin.management.dto.PluginAccountExportFile;
import com.sky.job.plugin.management.dto.PluginAccountImportResponse;
import com.sky.job.plugin.management.dto.PluginAccountImportRowResult;
import com.sky.job.plugin.management.dto.PluginAccountResponse;
import com.sky.job.plugin.management.dto.PluginAccountSchemaResponse;
import com.sky.job.plugin.management.dto.PluginConfigResponse;
import com.sky.job.plugin.management.dto.UpdatePluginAccountStateRequest;
import com.sky.job.plugin.management.dto.UpdatePluginConfigRequest;
import com.sky.job.plugin.management.dto.UpsertPluginAccountRequest;
import com.sky.job.plugin.runtime.PluginRuntimeRecord;
import com.sky.job.plugin.security.PluginCapability;
import com.sky.job.plugin.storage.PluginAccount;
import com.sky.job.plugin.storage.PluginAccountStore;
import com.sky.job.plugin.storage.PluginAuditStore;
import com.sky.job.plugin.storage.PluginConfigStore;
import com.sky.job.plugin.storage.PluginPayloadValidator;
import com.sky.job.scheduler.PluginRuntimeMetadataStore;
import com.sky.job.scheduler.dto.PageResponse;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVPrinter;
import org.apache.commons.csv.CSVRecord;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Service
public class PluginManagementService {
    private static final Logger log = LoggerFactory.getLogger(PluginManagementService.class);
    private static final int MAX_IMPORT_ROWS = 5000;
    private static final int MAX_EXPORT_ROWS = 10000;
    private static final int MAX_IMPORT_ROW_RESULTS = 500;
    private static final String CSV_CONTENT_TYPE = "text/csv;charset=UTF-8";
    private static final String XLSX_CONTENT_TYPE = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";

    private final PluginRuntimeMetadataStore runtimeStore;
    private final PluginConfigStore configStore;
    private final PluginAccountStore accountStore;
    private final PluginPayloadValidator payloadValidator;
    private final PluginAuditStore auditStore;
    private final ObjectMapper objectMapper;

    public PluginManagementService(
            PluginRuntimeMetadataStore runtimeStore,
            PluginConfigStore configStore,
            PluginAccountStore accountStore,
            PluginPayloadValidator payloadValidator,
            PluginAuditStore auditStore,
            ObjectMapper objectMapper
    ) {
        this.runtimeStore = runtimeStore;
        this.configStore = configStore;
        this.accountStore = accountStore;
        this.payloadValidator = payloadValidator;
        this.auditStore = auditStore;
        this.objectMapper = objectMapper;
    }

    public PluginConfigResponse getConfig(String pluginId) {
        PluginRuntimeRecord runtime = requireCapability(pluginId, PluginCapability.CONFIG);
        return new PluginConfigResponse(pluginId, configStore.getConfigJson(pluginId), schema(runtime.configSchemaJson()));
    }

    public PluginConfigResponse updateConfig(String pluginId, UpdatePluginConfigRequest request, String actor) {
        PluginRuntimeRecord runtime = requireCapability(pluginId, PluginCapability.CONFIG);
        String configJson = payloadValidator.normalizeConfigJson(request == null ? null : request.configJson());
        configStore.upsertConfigJson(pluginId, configJson);
        audit(pluginId, "", actor, "PLUGIN_CONFIG_UPDATE", configDetail(configJson));
        return new PluginConfigResponse(pluginId, configJson, schema(runtime.configSchemaJson()));
    }

    public PageResponse<PluginAccountResponse> listAccounts(String pluginId, Boolean enabled, String accountId, String displayName, Integer page, Integer size) {
        requireCapability(pluginId, PluginCapability.ACCOUNT);
        return new PageResponse<>(
                accountStore.list(pluginId, enabled, accountId, displayName, page, size).stream().map(PluginAccountResponse::from).toList(),
                normalizedPage(page),
                normalizedSize(size),
                accountStore.count(pluginId, enabled, accountId, displayName)
        );
    }

    public PluginAccountExportFile exportAccounts(String pluginId, Boolean enabled, String accountId, String displayName, String format, Boolean includeSecret, String actor) {
        PluginRuntimeRecord runtime = requireCapability(pluginId, PluginCapability.ACCOUNT);
        AccountFileFormat fileFormat = exportFormat(format);
        boolean exportSecret = includeSecret != null && includeSecret;
        AccountFileSchema fileSchema = accountFileSchema(runtime.accountSchemaJson(), exportSecret);
        List<PluginAccount> accounts = accountStore.listManagedForExport(pluginId, enabled, accountId, displayName, MAX_EXPORT_ROWS + 1);
        if (accounts.size() > MAX_EXPORT_ROWS) {
            throw new IllegalArgumentException("Too many accounts to export; please narrow filters");
        }
        byte[] content = switch (fileFormat) {
            case CSV -> writeCsv(accounts, fileSchema);
            case XLSX -> writeXlsx(accounts, fileSchema);
        };
        audit(pluginId, "", actor, "PLUGIN_ACCOUNT_EXPORT", accountExportDetail(fileFormat.name().toLowerCase(Locale.ROOT), accounts.size(), exportSecret, enabled, accountId, displayName));
        return new PluginAccountExportFile(exportFilename(pluginId, fileFormat), fileFormat.contentType, content);
    }

    public PluginAccountImportResponse importAccounts(String pluginId, MultipartFile file, Boolean dryRun, Boolean enabledDefault, String actor) {
        PluginRuntimeRecord runtime = requireCapability(pluginId, PluginCapability.ACCOUNT);
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Import file is required");
        }
        AccountFileFormat fileFormat = importFormat(file.getOriginalFilename());
        boolean dry = dryRun != null && dryRun;
        boolean defaultEnabled = enabledDefault == null || enabledDefault;
        AccountFileSchema fileSchema = accountFileSchema(runtime.accountSchemaJson(), true);
        List<ImportedAccountRow> rows = parseImportRows(file, fileFormat, fileSchema);
        if (rows.size() > MAX_IMPORT_ROWS) {
            throw new IllegalArgumentException("Too many account rows to import");
        }

        Set<String> seenAccountIds = new HashSet<>();
        List<PluginAccountImportRowResult> results = new ArrayList<>();
        int successRows = 0;
        int failedRows = 0;
        int createdRows = 0;
        int updatedRows = 0;

        for (ImportedAccountRow row : rows) {
            try {
                AccountImportItem item = validateImportRow(row, fileSchema, defaultEnabled);
                if (!seenAccountIds.add(item.accountId())) {
                    throw new IllegalArgumentException("Duplicate accountId in import file");
                }
                boolean exists = accountStore.getManaged(pluginId, item.accountId()).isPresent();
                if (!dry) {
                    accountStore.upsert(pluginId, item.accountId(), item.displayName(), item.accountJson(), item.enabled());
                }
                String operation = exists ? "UPDATE" : "CREATE";
                if (exists) {
                    updatedRows++;
                } else {
                    createdRows++;
                }
                successRows++;
                addRowResult(results, new PluginAccountImportRowResult(row.rowNumber(), item.accountId(), "SUCCESS", operation, dry ? "Validated" : "Imported"));
            } catch (Exception exception) {
                failedRows++;
                addRowResult(results, new PluginAccountImportRowResult(row.rowNumber(), row.values().get(fileSchema.accountIdField()), "FAILED", "NONE", exception.getMessage()));
            }
        }

        PluginAccountImportResponse response = new PluginAccountImportResponse(pluginId, dry, rows.size(), successRows, failedRows, createdRows, updatedRows, results);
        audit(pluginId, "", actor, "PLUGIN_ACCOUNT_IMPORT", accountImportDetail(fileFormat.name().toLowerCase(Locale.ROOT), response));
        return response;
    }

    public PluginAccountSchemaResponse getAccountSchema(String pluginId) {
        PluginRuntimeRecord runtime = requireCapability(pluginId, PluginCapability.ACCOUNT);
        return new PluginAccountSchemaResponse(pluginId, schema(runtime.accountSchemaJson()));
    }

    public PluginAccountResponse getAccount(String pluginId, String accountId) {
        requireCapability(pluginId, PluginCapability.ACCOUNT);
        payloadValidator.validateAccountId(accountId);
        return accountStore.getManaged(pluginId, accountId).map(PluginAccountResponse::from).orElseThrow(() -> new IllegalArgumentException("Plugin account not found"));
    }

    public PluginAccountResponse createAccount(String pluginId, UpsertPluginAccountRequest request, String actor) {
        if (request == null || request.accountId() == null || request.accountId().isBlank()) {
            throw new IllegalArgumentException("Invalid account id");
        }
        String accountJson = payloadValidator.normalizeAccountJson(request.accountJson());
        PluginAccountResponse response = upsertAccount(pluginId, request.accountId(), request);
        audit(pluginId, "", actor, "PLUGIN_ACCOUNT_CREATE", accountDetail("create", response, accountJson));
        return response;
    }

    public PluginAccountResponse updateAccount(String pluginId, String accountId, UpsertPluginAccountRequest request, String actor) {
        requireCapability(pluginId, PluginCapability.ACCOUNT);
        payloadValidator.validateAccountId(accountId);
        PluginAccount previous = accountStore.getManaged(pluginId, accountId).orElseThrow(() -> new IllegalArgumentException("Plugin account not found"));
        String accountJson = payloadValidator.normalizeAccountJson(request == null ? null : request.accountJson());
        PluginAccountResponse response = upsertAccount(pluginId, accountId, request);
        audit(pluginId, "", actor, "PLUGIN_ACCOUNT_UPDATE", accountUpdateDetail(response, previous, accountJson));
        return response;
    }

    public PluginAccountResponse updateAccountEnabled(String pluginId, String accountId, UpdatePluginAccountStateRequest request, String actor) {
        requireCapability(pluginId, PluginCapability.ACCOUNT);
        payloadValidator.validateAccountId(accountId);
        PluginAccount previous = accountStore.getManaged(pluginId, accountId).orElseThrow(() -> new IllegalArgumentException("Plugin account not found"));
        if (accountStore.updateEnabled(pluginId, accountId, request.enabled()) == 0) {
            throw new IllegalArgumentException("Plugin account not found");
        }
        PluginAccountResponse response = getAccount(pluginId, accountId);
        audit(pluginId, "", actor, request.enabled() ? "PLUGIN_ACCOUNT_ENABLE" : "PLUGIN_ACCOUNT_DISABLE", accountStateDetail(request.enabled() ? "enable" : "disable", response, previous.enabled()));
        return response;
    }

    public BatchUpdatePluginAccountStateResponse updateAccountsEnabled(String pluginId, BatchUpdatePluginAccountStateRequest request, String actor) {
        requireCapability(pluginId, PluginCapability.ACCOUNT);
        if (request == null || request.accountIds() == null || request.accountIds().isEmpty()) {
            throw new IllegalArgumentException("Account ids are required");
        }
        List<BatchPluginAccountStateReceipt> receipts = new ArrayList<>();
        Set<String> seenAccountIds = new HashSet<>();
        int succeeded = 0;
        int failed = 0;
        for (String accountId : request.accountIds()) {
            try {
                if (accountId == null || accountId.isBlank()) {
                    throw new IllegalArgumentException("Invalid account id");
                }
                String normalizedAccountId = accountId.trim();
                payloadValidator.validateAccountId(normalizedAccountId);
                if (!seenAccountIds.add(normalizedAccountId)) {
                    throw new IllegalArgumentException("Duplicate account id");
                }
                if (accountStore.getManaged(pluginId, normalizedAccountId).isEmpty()) {
                    throw new IllegalArgumentException("Plugin account not found");
                }
                if (accountStore.updateEnabled(pluginId, normalizedAccountId, request.enabled()) == 0) {
                    throw new IllegalArgumentException("Plugin account not found");
                }
                succeeded++;
                receipts.add(new BatchPluginAccountStateReceipt(normalizedAccountId, true, "Updated"));
            } catch (Exception exception) {
                failed++;
                receipts.add(new BatchPluginAccountStateReceipt(accountId, false, exception.getMessage()));
            }
        }
        BatchUpdatePluginAccountStateResponse response = new BatchUpdatePluginAccountStateResponse(pluginId, request.enabled(), request.accountIds().size(), succeeded, failed, receipts);
        audit(pluginId, "", actor, request.enabled() ? "PLUGIN_ACCOUNT_BATCH_ENABLE" : "PLUGIN_ACCOUNT_BATCH_DISABLE", accountBatchStateDetail(response));
        return response;
    }

    public void deleteAccount(String pluginId, String accountId, String actor) {
        requireCapability(pluginId, PluginCapability.ACCOUNT);
        payloadValidator.validateAccountId(accountId);
        PluginAccount previous = accountStore.getManaged(pluginId, accountId).orElseThrow(() -> new IllegalArgumentException("Plugin account not found"));
        if (accountStore.delete(pluginId, accountId) == 0) {
            throw new IllegalArgumentException("Plugin account not found");
        }
        audit(pluginId, "", actor, "PLUGIN_ACCOUNT_DELETE", accountDeleteDetail(previous));
    }

    private List<ImportedAccountRow> parseImportRows(MultipartFile file, AccountFileFormat format, AccountFileSchema fileSchema) {
        try {
            return switch (format) {
                case CSV -> parseCsvRows(file, fileSchema);
                case XLSX -> parseXlsxRows(file, fileSchema);
            };
        } catch (IOException exception) {
            throw new IllegalArgumentException("Failed to read import file", exception);
        }
    }

    private List<ImportedAccountRow> parseCsvRows(MultipartFile file, AccountFileSchema fileSchema) throws IOException {
        try (Reader reader = new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8);
             CSVParser parser = CSVFormat.DEFAULT.builder()
                     .setHeader()
                     .setSkipHeaderRecord(true)
                     .setTrim(true)
                     .build()
                     .parse(reader)) {
            if (!hasHeader(parser.getHeaderMap(), fileSchema.accountIdField())) {
                throw new IllegalArgumentException("Import file is missing account id field header: " + fileSchema.accountIdField());
            }
            List<ImportedAccountRow> rows = new ArrayList<>();
            for (CSVRecord record : parser) {
                Map<String, String> values = new LinkedHashMap<>();
                for (String field : fileSchema.headers()) {
                    values.put(field, csvValue(record, field));
                }
                rows.add(new ImportedAccountRow((int) record.getRecordNumber() + 1, values));
            }
            return rows;
        }
    }

    private List<ImportedAccountRow> parseXlsxRows(MultipartFile file, AccountFileSchema fileSchema) throws IOException {
        try (Workbook workbook = new XSSFWorkbook(file.getInputStream())) {
            Sheet sheet = workbook.getNumberOfSheets() == 0 ? null : workbook.getSheetAt(0);
            if (sheet == null || sheet.getPhysicalNumberOfRows() == 0) {
                throw new IllegalArgumentException("Import file is empty");
            }
            DataFormatter formatter = new DataFormatter();
            Map<String, Integer> headers = xlsxHeaders(sheet.getRow(0), formatter);
            if (!headers.containsKey(fileSchema.accountIdField())) {
                throw new IllegalArgumentException("Import file is missing account id field header: " + fileSchema.accountIdField());
            }
            List<ImportedAccountRow> rows = new ArrayList<>();
            for (int i = 1; i <= sheet.getLastRowNum(); i++) {
                Row row = sheet.getRow(i);
                if (isBlankXlsxRow(row, headers, formatter, fileSchema)) {
                    continue;
                }
                Map<String, String> values = new LinkedHashMap<>();
                for (String field : fileSchema.headers()) {
                    values.put(field, xlsxValue(row, headers, formatter, field));
                }
                rows.add(new ImportedAccountRow(i + 1, values));
            }
            return rows;
        }
    }

    private byte[] writeCsv(List<PluginAccount> accounts, AccountFileSchema fileSchema) {
        try (StringWriter writer = new StringWriter(); CSVPrinter printer = new CSVPrinter(writer, CSVFormat.DEFAULT)) {
            writer.write('﻿');
            printer.printRecord(fileSchema.headers());
            for (PluginAccount account : accounts) {
                Map<String, String> values = exportFieldValues(account, fileSchema);
                printer.printRecord(fileSchema.headers().stream().map(values::get).toList());
            }
            printer.flush();
            return writer.toString().getBytes(StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to write CSV export", exception);
        }
    }

    private byte[] writeXlsx(List<PluginAccount> accounts, AccountFileSchema fileSchema) {
        try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("accounts");
            Row header = sheet.createRow(0);
            for (int i = 0; i < fileSchema.headers().size(); i++) {
                header.createCell(i).setCellValue(fileSchema.headers().get(i));
            }
            for (int i = 0; i < accounts.size(); i++) {
                Map<String, String> values = exportFieldValues(accounts.get(i), fileSchema);
                Row row = sheet.createRow(i + 1);
                for (int j = 0; j < fileSchema.headers().size(); j++) {
                    row.createCell(j).setCellValue(value(values.get(fileSchema.headers().get(j))));
                }
            }
            for (int i = 0; i < fileSchema.headers().size(); i++) {
                sheet.autoSizeColumn(i);
            }
            workbook.write(outputStream);
            return outputStream.toByteArray();
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to write XLSX export", exception);
        }
    }

    private AccountImportItem validateImportRow(ImportedAccountRow row, AccountFileSchema fileSchema, boolean enabledDefault) {
        String accountId = row.values().get(fileSchema.accountIdField());
        if (accountId == null || accountId.isBlank()) {
            throw new IllegalArgumentException("Invalid account id");
        }
        accountId = accountId.trim();
        payloadValidator.validateAccountId(accountId);
        ObjectNode accountJsonNode = objectMapper.createObjectNode();
        for (String field : fileSchema.importFields()) {
            accountJsonNode.put(field, value(row.values().get(field)));
        }
        String accountJson = payloadValidator.normalizeAccountJson(accountJsonNode.toString());
        boolean enabled = parseEnabled(row.values().get("enabled"), enabledDefault);
        return new AccountImportItem(accountId, displayName(null, accountId), accountJson, enabled);
    }

    private boolean parseEnabled(String value, boolean defaultValue) {
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "true", "1", "yes", "y", "是" -> true;
            case "false", "0", "no", "n", "否" -> false;
            default -> throw new IllegalArgumentException("Invalid enabled value");
        };
    }

    private AccountFileFormat exportFormat(String value) {
        if (value == null || value.isBlank() || value.equalsIgnoreCase("csv")) {
            return AccountFileFormat.CSV;
        }
        if (value.equalsIgnoreCase("xlsx") || value.equalsIgnoreCase("excel")) {
            return AccountFileFormat.XLSX;
        }
        throw new IllegalArgumentException("Unsupported export format");
    }

    private AccountFileFormat importFormat(String filename) {
        if (filename == null || filename.isBlank()) {
            throw new IllegalArgumentException("Import file name is required");
        }
        String normalized = filename.toLowerCase(Locale.ROOT);
        if (normalized.endsWith(".csv")) {
            return AccountFileFormat.CSV;
        }
        if (normalized.endsWith(".xlsx")) {
            return AccountFileFormat.XLSX;
        }
        throw new IllegalArgumentException("Unsupported import file type");
    }

    private String csvValue(CSVRecord record, String header) {
        String actualHeader = actualHeader(record.getParser().getHeaderMap(), header);
        return actualHeader == null || !record.isMapped(actualHeader) ? "" : record.get(actualHeader).trim();
    }

    private Map<String, String> exportFieldValues(PluginAccount account, AccountFileSchema fileSchema) {
        Map<String, String> values = new LinkedHashMap<>();
        JsonNode root = accountJson(account.accountJson());
        for (String field : fileSchema.importFields()) {
            JsonNode value = root.path(field);
            values.put(field, value.isMissingNode() || value.isNull() ? "" : value.asText(""));
        }
        if (fileSchema.headers().contains("enabled")) {
            values.put("enabled", String.valueOf(account.enabled()));
        }
        return values;
    }

    private AccountFileSchema accountFileSchema(String accountSchemaJson, boolean includeSensitive) {
        try {
            JsonNode root = objectMapper.readTree(schema(accountSchemaJson));
            String accountIdField = root.path("accountIdField").asText("").trim();
            if (accountIdField.isBlank()) {
                throw new IllegalArgumentException("Plugin account schema is missing accountIdField");
            }
            List<String> importFields = new ArrayList<>();
            JsonNode fields = root.path("fields");
            if (fields.isArray()) {
                fields.forEach(field -> {
                    String name = field.path("name").asText("").trim();
                    boolean sensitive = field.path("sensitive").asBoolean(false);
                    if (!name.isBlank() && (includeSensitive || !sensitive)) {
                        importFields.add(name);
                    }
                });
            }
            if (!importFields.contains(accountIdField)) {
                importFields.add(0, accountIdField);
            }
            List<String> headers = new ArrayList<>(importFields);
            headers.add("enabled");
            return new AccountFileSchema(accountIdField, List.copyOf(importFields), List.copyOf(headers));
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("Invalid plugin account schema", exception);
        }
    }

    private JsonNode accountJson(String accountJson) {
        try {
            return objectMapper.readTree(accountJson == null || accountJson.isBlank() ? "{}" : accountJson);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Invalid stored account JSON", exception);
        }
    }

    private boolean hasHeader(Map<String, Integer> headers, String header) {
        return actualHeader(headers, header) != null;
    }

    private String actualHeader(Map<String, Integer> headers, String header) {
        for (String existing : headers.keySet()) {
            if (normalizeHeader(existing).equals(header)) {
                return existing;
            }
        }
        return null;
    }

    private Map<String, Integer> xlsxHeaders(Row headerRow, DataFormatter formatter) {
        Map<String, Integer> headers = new HashMap<>();
        if (headerRow == null) {
            return headers;
        }
        for (int i = 0; i < headerRow.getLastCellNum(); i++) {
            String header = normalizeHeader(formatter.formatCellValue(headerRow.getCell(i)));
            if (!header.isBlank()) {
                headers.put(header, i);
            }
        }
        return headers;
    }

    private boolean isBlankXlsxRow(Row row, Map<String, Integer> headers, DataFormatter formatter, AccountFileSchema fileSchema) {
        if (row == null) {
            return true;
        }
        for (String header : fileSchema.headers()) {
            if (!xlsxValue(row, headers, formatter, header).isBlank()) {
                return false;
            }
        }
        return true;
    }

    private String xlsxValue(Row row, Map<String, Integer> headers, DataFormatter formatter, String header) {
        Integer index = headers.get(header);
        if (row == null || index == null) {
            return "";
        }
        return formatter.formatCellValue(row.getCell(index)).trim();
    }

    private String normalizeHeader(String value) {
        return value == null ? "" : value.replace("﻿", "").trim();
    }

    private void addRowResult(List<PluginAccountImportRowResult> results, PluginAccountImportRowResult result) {
        if (results.size() < MAX_IMPORT_ROW_RESULTS || "FAILED".equals(result.status())) {
            results.add(result);
        }
    }

    private String exportFilename(String pluginId, AccountFileFormat format) {
        return "plugin-" + pluginId.replaceAll("[^a-zA-Z0-9._-]", "_") + "-accounts." + format.extension;
    }

    private PluginAccountResponse upsertAccount(String pluginId, String accountId, UpsertPluginAccountRequest request) {
        requireCapability(pluginId, PluginCapability.ACCOUNT);
        payloadValidator.validateAccountId(accountId);
        String displayName = displayName(request == null ? null : request.displayName(), accountId);
        String accountJson = payloadValidator.normalizeAccountJson(request == null ? null : request.accountJson());
        boolean enabled = request == null || request.enabled() == null || request.enabled();
        accountStore.upsert(pluginId, accountId, displayName, accountJson, enabled);
        PluginAccount account = accountStore.getManaged(pluginId, accountId).orElseThrow(() -> new IllegalStateException("Plugin account was not saved"));
        return PluginAccountResponse.from(account);
    }

    private Map<String, Object> configDetail(String configJson) {
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("operation", "update");
        detail.put("capability", PluginCapability.CONFIG.name());
        detail.put("configPresent", hasJsonPayload(configJson));
        detail.put("configBytes", utf8Bytes(configJson));
        return detail;
    }

    private Map<String, Object> accountDetail(String operation, PluginAccountResponse account, String accountJson) {
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("operation", operation);
        detail.put("capability", PluginCapability.ACCOUNT.name());
        detail.put("accountId", account.accountId());
        detail.put("displayName", account.displayName());
        detail.put("enabled", account.enabled());
        detail.put("accountJsonPresent", hasJsonPayload(accountJson));
        detail.put("accountJsonBytes", utf8Bytes(accountJson));
        return detail;
    }

    private Map<String, Object> accountUpdateDetail(PluginAccountResponse account, PluginAccount previous, String accountJson) {
        Map<String, Object> detail = accountDetail("update", account, accountJson);
        detail.put("previousDisplayName", previous.displayName());
        detail.put("previousEnabled", previous.enabled());
        return detail;
    }

    private Map<String, Object> accountStateDetail(String operation, PluginAccountResponse account, boolean previousEnabled) {
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("operation", operation);
        detail.put("capability", PluginCapability.ACCOUNT.name());
        detail.put("accountId", account.accountId());
        detail.put("displayName", account.displayName());
        detail.put("previousEnabled", previousEnabled);
        detail.put("enabled", account.enabled());
        return detail;
    }

    private Map<String, Object> accountDeleteDetail(PluginAccount account) {
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("operation", "delete");
        detail.put("capability", PluginCapability.ACCOUNT.name());
        detail.put("accountId", account.accountId());
        detail.put("displayName", account.displayName());
        detail.put("previousEnabled", account.enabled());
        detail.put("hadAccountJson", hasJsonPayload(account.accountJson()));
        return detail;
    }

    private Map<String, Object> accountBatchStateDetail(BatchUpdatePluginAccountStateResponse response) {
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("operation", response.enabled() ? "batch_enable" : "batch_disable");
        detail.put("capability", PluginCapability.ACCOUNT.name());
        detail.put("enabled", response.enabled());
        detail.put("total", response.total());
        detail.put("succeeded", response.succeeded());
        detail.put("failed", response.failed());
        return detail;
    }

    private Map<String, Object> accountImportDetail(String format, PluginAccountImportResponse response) {
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("operation", "import");
        detail.put("capability", PluginCapability.ACCOUNT.name());
        detail.put("format", format);
        detail.put("dryRun", response.dryRun());
        detail.put("totalRows", response.totalRows());
        detail.put("successRows", response.successRows());
        detail.put("failedRows", response.failedRows());
        detail.put("createdRows", response.createdRows());
        detail.put("updatedRows", response.updatedRows());
        return detail;
    }

    private Map<String, Object> accountExportDetail(String format, int rowCount, boolean includeSecret, Boolean enabled, String accountId, String displayName) {
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("operation", "export");
        detail.put("capability", PluginCapability.ACCOUNT.name());
        detail.put("format", format);
        detail.put("rowCount", rowCount);
        detail.put("includeSecret", includeSecret);
        detail.put("enabled", enabled);
        detail.put("accountId", accountId);
        detail.put("displayName", displayName);
        return detail;
    }

    private void audit(String pluginId, String instanceId, String actor, String action, Map<String, Object> detail) {
        try {
            auditStore.insert(pluginId, instanceId, actor, action, objectMapper.writeValueAsString(detail));
        } catch (JsonProcessingException exception) {
            log.warn("Failed to serialize plugin management audit detail: pluginId={}, action={}", pluginId, action, exception);
        } catch (RuntimeException exception) {
            log.warn("Failed to write plugin management audit log: pluginId={}, action={}", pluginId, action, exception);
        }
    }

    private int utf8Bytes(String value) {
        return value == null ? 0 : value.getBytes(StandardCharsets.UTF_8).length;
    }

    private boolean hasJsonPayload(String normalizedJson) {
        return normalizedJson != null && !normalizedJson.isBlank();
    }

    private PluginRuntimeRecord requireCapability(String pluginId, PluginCapability capability) {
        PluginRuntimeRecord runtime = runtimeStore.findRuntimeByPluginId(pluginId).orElseThrow(() -> new IllegalArgumentException("Plugin runtime not found"));
        Set<PluginCapability> capabilities = runtimeStore.runtimeCapabilities(runtime.capabilitiesJson());
        if (!capabilities.contains(capability)) {
            String name = capability == PluginCapability.CONFIG ? "config" : "account";
            throw new IllegalArgumentException("Plugin does not support " + name + " management");
        }
        return runtime;
    }

    private String displayName(String displayName, String accountId) {
        String value = displayName == null || displayName.isBlank() ? accountId : displayName.trim();
        if (value.length() > 255) {
            throw new IllegalArgumentException("Display name is too long");
        }
        return value;
    }

    private String schema(String schemaJson) {
        return schemaJson == null || schemaJson.isBlank() ? "{}" : schemaJson;
    }

    private String value(String value) {
        return value == null ? "" : value;
    }

    private int normalizedPage(Integer page) {
        return Math.max(page == null ? 1 : page, 1);
    }

    private int normalizedSize(Integer size) {
        return Math.min(Math.max(size == null ? 20 : size, 1), 100);
    }

    private enum AccountFileFormat {
        CSV("csv", CSV_CONTENT_TYPE),
        XLSX("xlsx", XLSX_CONTENT_TYPE);

        private final String extension;
        private final String contentType;

        AccountFileFormat(String extension, String contentType) {
            this.extension = extension;
            this.contentType = contentType;
        }
    }

    private record ImportedAccountRow(
            int rowNumber,
            Map<String, String> values
    ) {
    }

    private record AccountFileSchema(
            String accountIdField,
            List<String> importFields,
            List<String> headers
    ) {
    }

    private record AccountImportItem(
            String accountId,
            String displayName,
            String accountJson,
            boolean enabled
    ) {
    }
}
