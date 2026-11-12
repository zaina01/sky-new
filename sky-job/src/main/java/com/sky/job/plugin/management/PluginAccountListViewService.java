package com.sky.job.plugin.management;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.NullNode;
import com.sky.job.plugin.management.dto.PluginAccountListFieldResponse;
import com.sky.job.plugin.management.dto.PluginAccountListItemResponse;
import com.sky.job.plugin.management.dto.PluginAccountViewPreferenceResponse;
import com.sky.job.plugin.management.dto.UpdatePluginAccountViewPreferenceRequest;
import com.sky.job.plugin.storage.PluginAccount;
import com.sky.job.plugin.storage.PluginAccountListPreferenceStore;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class PluginAccountListViewService {
    public static final int MAX_FIELDS = 5;

    private static final Set<String> DISPLAYABLE_TYPES = Set.of(
            "TEXT",
            "NUMBER",
            "SWITCH",
            "RADIO",
            "SELECT",
            "CHECKBOX",
            "MULTI_SELECT",
            "DATE",
            "TIME",
            "DATETIME"
    );

    private final PluginAccountListPreferenceStore preferenceStore;
    private final ObjectMapper objectMapper;

    public PluginAccountListViewService(PluginAccountListPreferenceStore preferenceStore, ObjectMapper objectMapper) {
        this.preferenceStore = preferenceStore;
        this.objectMapper = objectMapper;
    }

    public PluginAccountViewPreferenceResponse getPreference(
            String installationId,
            String userId,
            String accountSchemaJson
    ) {
        AccountListSchema schema = accountListSchema(accountSchemaJson);
        List<String> fields = reconciledFields(userId, installationId, schema);
        return response(installationId, fields, schema);
    }

    public PluginAccountViewPreferenceResponse updatePreference(
            String installationId,
            String userId,
            String accountSchemaJson,
            UpdatePluginAccountViewPreferenceRequest request
    ) {
        if (request == null || request.fields() == null) {
            throw new IllegalArgumentException("Account list fields are required");
        }
        AccountListSchema schema = accountListSchema(accountSchemaJson);
        List<String> fields = validatedFields(request.fields(), schema);
        if (fields.isEmpty()) {
            preferenceStore.delete(userId, installationId);
        } else {
            preferenceStore.upsertFields(userId, installationId, fields);
        }
        return response(installationId, fields, schema);
    }

    public List<PluginAccountListItemResponse> listItems(
            String installationId,
            String userId,
            String accountSchemaJson,
            List<PluginAccount> accounts
    ) {
        AccountListSchema schema = accountListSchema(accountSchemaJson);
        List<String> fields = reconciledFields(userId, installationId, schema);
        return accounts.stream()
                .map(account -> listItem(account, fields, schema))
                .toList();
    }

    private PluginAccountViewPreferenceResponse response(
            String installationId,
            List<String> fields,
            AccountListSchema schema
    ) {
        return new PluginAccountViewPreferenceResponse(
                installationId,
                fields,
                schema.availableFields(),
                MAX_FIELDS
        );
    }

    private List<String> validatedFields(List<String> requestedFields, AccountListSchema schema) {
        if (requestedFields.size() > MAX_FIELDS) {
            throw new IllegalArgumentException("At most " + MAX_FIELDS + " plugin account list fields may be selected");
        }
        List<String> fields = new ArrayList<>(requestedFields.size());
        Set<String> seen = new LinkedHashSet<>();
        for (String requestedField : requestedFields) {
            if (requestedField == null || requestedField.isBlank()) {
                throw new IllegalArgumentException("Account list field name must not be blank");
            }
            String field = requestedField.trim();
            if (!seen.add(field)) {
                throw new IllegalArgumentException("Duplicate account list field: " + field);
            }
            if (!schema.fieldsByName().containsKey(field)) {
                throw new IllegalArgumentException("Account list field is not available: " + field);
            }
            fields.add(field);
        }
        return List.copyOf(fields);
    }

    private List<String> reconciledFields(String userId, String installationId, AccountListSchema schema) {
        var stored = preferenceStore.findFields(userId, installationId);
        if (stored.isEmpty()) {
            return List.of();
        }
        List<String> original = stored.get();
        LinkedHashSet<String> valid = new LinkedHashSet<>();
        for (String field : original) {
            if (field != null && schema.fieldsByName().containsKey(field) && valid.size() < MAX_FIELDS) {
                valid.add(field);
            }
        }
        List<String> reconciled = List.copyOf(valid);
        if (!original.equals(reconciled)) {
            if (reconciled.isEmpty()) {
                preferenceStore.delete(userId, installationId);
            } else {
                preferenceStore.upsertFields(userId, installationId, reconciled);
            }
        }
        return reconciled;
    }

    private PluginAccountListItemResponse listItem(
            PluginAccount account,
            List<String> fields,
            AccountListSchema schema
    ) {
        JsonNode accountJson = accountJson(account.accountJson());
        Map<String, JsonNode> displayValues = new LinkedHashMap<>();
        for (String fieldName : fields) {
            AccountListField field = schema.fieldsByName().get(fieldName);
            JsonNode value = isVisible(field, accountJson) ? normalizedDisplayValue(accountJson.get(fieldName)) : NullNode.getInstance();
            displayValues.put(fieldName, value);
        }
        return new PluginAccountListItemResponse(
                account.installationId(),
                account.accountId(),
                account.displayName(),
                account.enabled(),
                account.createdAt(),
                account.updatedAt(),
                displayValues,
                account.groupId(),
                account.groupName()
        );
    }

    private boolean isVisible(AccountListField field, JsonNode accountJson) {
        JsonNode visibleWhen = field.visibleWhen();
        if (visibleWhen == null || !visibleWhen.isObject()) {
            return true;
        }
        String controlField = visibleWhen.path("field").asText("");
        if (controlField.isBlank()) {
            return true;
        }
        JsonNode controlValue = accountJson.get(controlField);
        if (controlValue == null || controlValue.isNull()) {
            return false;
        }
        JsonNode equals = visibleWhen.get("equals");
        if (equals != null) {
            return conditionMatches(controlValue, equals);
        }
        JsonNode in = visibleWhen.get("in");
        if (in != null && in.isArray()) {
            for (JsonNode candidate : in) {
                if (conditionMatches(controlValue, candidate)) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean conditionMatches(JsonNode actual, JsonNode expected) {
        if (actual.equals(expected)) {
            return true;
        }
        return actual.isValueNode()
                && expected != null
                && expected.isValueNode()
                && actual.asText().equals(expected.asText());
    }

    private JsonNode normalizedDisplayValue(JsonNode value) {
        if (value == null || value.isNull() || value.isMissingNode()) {
            return NullNode.getInstance();
        }
        if (value.isTextual() && value.asText().isBlank()) {
            return NullNode.getInstance();
        }
        if (value.isContainerNode() && value.isEmpty()) {
            return NullNode.getInstance();
        }
        return value.deepCopy();
    }

    private AccountListSchema accountListSchema(String accountSchemaJson) {
        try {
            JsonNode root = objectMapper.readTree(accountSchemaJson == null || accountSchemaJson.isBlank() ? "{}" : accountSchemaJson);
            if (root == null || !root.isObject()) {
                throw new IllegalArgumentException("Plugin account schema must be a JSON object");
            }
            String accountIdField = root.path("accountIdField").asText("");
            Map<String, AccountListField> fieldsByName = new LinkedHashMap<>();
            List<PluginAccountListFieldResponse> availableFields = new ArrayList<>();
            JsonNode fields = root.path("fields");
            if (fields.isArray()) {
                for (JsonNode field : fields) {
                    String name = field.path("name").asText("").trim();
                    String type = field.path("type").asText("").trim();
                    boolean accountId = field.path("accountId").asBoolean(false) || name.equals(accountIdField);
                    boolean sensitive = field.path("sensitive").asBoolean(false);
                    if (name.isBlank() || accountId || sensitive || !DISPLAYABLE_TYPES.contains(type)) {
                        continue;
                    }
                    List<String> options = stringValues(field.path("options"));
                    AccountListField listField = new AccountListField(name, field.get("visibleWhen"));
                    if (fieldsByName.putIfAbsent(name, listField) == null) {
                        String label = field.path("label").asText("").trim();
                        availableFields.add(new PluginAccountListFieldResponse(
                                name,
                                label.isBlank() ? name : label,
                                type,
                                options
                        ));
                    }
                }
            }
            return new AccountListSchema(Map.copyOf(fieldsByName), List.copyOf(availableFields));
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("Invalid plugin account schema", exception);
        }
    }

    private List<String> stringValues(JsonNode values) {
        if (!values.isArray()) {
            return List.of();
        }
        List<String> result = new ArrayList<>();
        for (JsonNode value : values) {
            if (value.isTextual()) {
                result.add(value.asText());
            }
        }
        return List.copyOf(result);
    }

    private JsonNode accountJson(String accountJson) {
        try {
            JsonNode root = objectMapper.readTree(accountJson == null || accountJson.isBlank() ? "{}" : accountJson);
            if (root == null || !root.isObject()) {
                throw new IllegalStateException("Stored plugin account JSON must be an object");
            }
            return root;
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Invalid stored account JSON", exception);
        }
    }

    private record AccountListSchema(
            Map<String, AccountListField> fieldsByName,
            List<PluginAccountListFieldResponse> availableFields
    ) {
    }

    private record AccountListField(
            String name,
            JsonNode visibleWhen
    ) {
    }
}
