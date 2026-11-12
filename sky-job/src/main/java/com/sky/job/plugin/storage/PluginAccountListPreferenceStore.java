package com.sky.job.plugin.storage;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sky.job.plugin.storage.mapper.PluginAccountListPreferenceMapper;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public class PluginAccountListPreferenceStore {
    private static final TypeReference<List<String>> STRING_LIST = new TypeReference<>() {
    };

    private final PluginAccountListPreferenceMapper mapper;
    private final ObjectMapper objectMapper;

    public PluginAccountListPreferenceStore(PluginAccountListPreferenceMapper mapper, ObjectMapper objectMapper) {
        this.mapper = mapper;
        this.objectMapper = objectMapper;
    }

    public Optional<List<String>> findFields(String userId, String installationId) {
        String json = mapper.findSelectedFieldsJson(userId, installationId);
        if (json == null) {
            return Optional.empty();
        }
        try {
            List<String> fields = objectMapper.readValue(json, STRING_LIST);
            return Optional.of(fields == null ? List.of() : List.copyOf(fields));
        } catch (JsonProcessingException | NullPointerException exception) {
            throw new IllegalStateException("Invalid stored plugin account list preference", exception);
        }
    }

    public void upsertFields(String userId, String installationId, List<String> fields) {
        try {
            mapper.upsert(userId, installationId, objectMapper.writeValueAsString(fields));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Failed to serialize plugin account list preference", exception);
        }
    }

    public int delete(String userId, String installationId) {
        return mapper.delete(userId, installationId);
    }

    public int deleteByInstallationId(String installationId) {
        return mapper.deleteByInstallationId(installationId);
    }
}
