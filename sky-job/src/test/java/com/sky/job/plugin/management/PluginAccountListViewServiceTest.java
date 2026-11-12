package com.sky.job.plugin.management;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sky.job.plugin.management.dto.UpdatePluginAccountViewPreferenceRequest;
import com.sky.job.plugin.storage.PluginAccount;
import com.sky.job.plugin.storage.PluginAccountListPreferenceStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PluginAccountListViewServiceTest {
    private static final String INSTALLATION_ID = "installation-a";
    private static final String USER_ID = "user-a";
    private static final String SCHEMA = """
            {
              "accountIdField": "accountId",
              "fields": [
                {"name":"accountId","label":"Account ID","type":"TEXT","accountId":true,"sensitive":false,"options":[]},
                {"name":"username","label":"Username","type":"TEXT","accountId":false,"sensitive":false,"options":[]},
                {"name":"region","label":"Region","type":"SELECT","accountId":false,"sensitive":false,"options":["east","west"]},
                {"name":"tags","label":"Tags","type":"MULTI_SELECT","accountId":false,"sensitive":false,"options":["primary","backup"]},
                {"name":"enabledByPlugin","label":"Plugin enabled","type":"SWITCH","accountId":false,"sensitive":false,"options":[]},
                {"name":"expireAt","label":"Expires","type":"DATETIME","accountId":false,"sensitive":false,"options":[],"visibleWhen":{"field":"enabledByPlugin","equals":true}},
                {"name":"secret","label":"Secret","type":"TEXT","accountId":false,"sensitive":true,"options":[]},
                {"name":"password","label":"Password","type":"PASSWORD","accountId":false,"sensitive":false,"options":[]},
                {"name":"metadata","label":"Metadata","type":"MAP","accountId":false,"sensitive":false,"options":[]},
                {"name":"credentials","label":"Credentials","type":"OBJECT_LIST","accountId":false,"sensitive":false,"options":[]}
                ,{"name":"aliases","label":"Aliases","type":"LIST","accountId":false,"sensitive":false,"options":[]}
              ]
            }
            """;

    private PluginAccountListPreferenceStore preferenceStore;
    private PluginAccountListViewService service;

    @BeforeEach
    void setUp() {
        preferenceStore = mock(PluginAccountListPreferenceStore.class);
        service = new PluginAccountListViewService(preferenceStore, new ObjectMapper());
    }

    @Test
    void exposesOnlySafeTopLevelSimpleFields() {
        when(preferenceStore.findFields(USER_ID, INSTALLATION_ID)).thenReturn(Optional.empty());

        var response = service.getPreference(INSTALLATION_ID, USER_ID, SCHEMA);

        assertThat(response.fields()).isEmpty();
        assertThat(response.maxFields()).isEqualTo(5);
        assertThat(response.availableFields()).extracting(field -> field.name())
                .containsExactly("username", "region", "tags", "enabledByPlugin", "expireAt");
        assertThat(response.availableFields()).extracting(field -> field.name())
                .doesNotContain("accountId", "secret", "password", "metadata", "credentials", "aliases");
        assertThat(response.availableFields().get(1).options()).containsExactly("east", "west");
    }

    @Test
    void savesFieldsInUserOrderAndEmptySelectionRestoresDefault() {
        var saved = service.updatePreference(
                INSTALLATION_ID,
                USER_ID,
                SCHEMA,
                new UpdatePluginAccountViewPreferenceRequest(List.of("region", "username"))
        );

        assertThat(saved.fields()).containsExactly("region", "username");
        verify(preferenceStore).upsertFields(USER_ID, INSTALLATION_ID, List.of("region", "username"));

        var reset = service.updatePreference(
                INSTALLATION_ID,
                USER_ID,
                SCHEMA,
                new UpdatePluginAccountViewPreferenceRequest(List.of())
        );

        assertThat(reset.fields()).isEmpty();
        verify(preferenceStore).delete(USER_ID, INSTALLATION_ID);
    }

    @Test
    void rejectsInvalidSelections() {
        assertThatThrownBy(() -> update(List.of("username", "username")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Duplicate");
        assertThatThrownBy(() -> update(List.of("unknown")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("not available");
        assertThatThrownBy(() -> update(List.of("accountId")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("not available");
        assertThatThrownBy(() -> update(List.of("secret")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("not available");
        assertThatThrownBy(() -> update(List.of("password")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("not available");
        assertThatThrownBy(() -> update(List.of("metadata")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("not available");
        assertThatThrownBy(() -> update(List.of("credentials")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("not available");
        assertThatThrownBy(() -> update(List.of("aliases")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("not available");
        assertThatThrownBy(() -> update(List.of("username", "region", "tags", "enabledByPlugin", "expireAt", "unknown")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("At most 5");

        verify(preferenceStore, never()).upsertFields(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyList());
    }

    @Test
    void projectsOriginalJsonTypesWithoutReturningSecretsAndHonorsVisibility() {
        when(preferenceStore.findFields(USER_ID, INSTALLATION_ID))
                .thenReturn(Optional.of(List.of("username", "region", "tags", "enabledByPlugin", "expireAt")));
        PluginAccount account = account("""
                {
                  "accountId":"account-a",
                  "username":"alice",
                  "region":"east",
                  "tags":["primary","backup"],
                  "enabledByPlugin":false,
                  "expireAt":"2026-12-31T23:59:59",
                  "secret":"must-not-leak",
                  "password":"must-not-leak-either"
                }
                """);

        var item = service.listItems(INSTALLATION_ID, USER_ID, SCHEMA, List.of(account)).getFirst();

        assertThat(item.displayValues()).containsOnlyKeys("username", "region", "tags", "enabledByPlugin", "expireAt");
        assertThat(item.displayValues().get("username").asText()).isEqualTo("alice");
        assertThat(item.displayValues().get("tags").isArray()).isTrue();
        assertThat(item.displayValues().get("enabledByPlugin").isBoolean()).isTrue();
        assertThat(item.displayValues().get("expireAt").isNull()).isTrue();
        assertThat(item.displayValues().toString()).doesNotContain("must-not-leak");
    }

    @Test
    void returnsNullForMissingAndEmptyValues() {
        when(preferenceStore.findFields(USER_ID, INSTALLATION_ID))
                .thenReturn(Optional.of(List.of("username", "region", "tags", "enabledByPlugin", "expireAt")));

        var item = service.listItems(
                INSTALLATION_ID,
                USER_ID,
                SCHEMA,
                List.of(account("{\"username\":\"\",\"tags\":[],\"enabledByPlugin\":true,\"expireAt\":null}"))
        ).getFirst();

        assertThat(item.displayValues().get("username").isNull()).isTrue();
        assertThat(item.displayValues().get("region").isNull()).isTrue();
        assertThat(item.displayValues().get("tags").isNull()).isTrue();
        assertThat(item.displayValues().get("enabledByPlugin").isBoolean()).isTrue();
        assertThat(item.displayValues().get("expireAt").isNull()).isTrue();
    }

    @Test
    void repairsPreferenceAfterSchemaChanges() {
        when(preferenceStore.findFields(USER_ID, INSTALLATION_ID))
                .thenReturn(Optional.of(List.of("username", "removed", "secret", "password", "metadata", "username", "region")));

        var response = service.getPreference(INSTALLATION_ID, USER_ID, SCHEMA);

        assertThat(response.fields()).containsExactly("username", "region");
        verify(preferenceStore).upsertFields(USER_ID, INSTALLATION_ID, List.of("username", "region"));
    }

    @Test
    void visibleWhenMatchesNumericJsonAgainstSchemaText() {
        String numericSchema = """
                {
                  "fields": [
                    {"name":"level","label":"Level","type":"NUMBER","accountId":false,"sensitive":false},
                    {"name":"note","label":"Note","type":"TEXT","accountId":false,"sensitive":false,
                     "visibleWhen":{"field":"level","equals":"2"}}
                  ]
                }
                """;
        when(preferenceStore.findFields(USER_ID, INSTALLATION_ID))
                .thenReturn(Optional.of(List.of("level", "note")));

        var item = service.listItems(
                INSTALLATION_ID,
                USER_ID,
                numericSchema,
                List.of(account("{\"level\":2,\"note\":\"visible\"}"))
        ).getFirst();

        assertThat(item.displayValues().get("level").isNumber()).isTrue();
        assertThat(item.displayValues().get("level").asInt()).isEqualTo(2);
        assertThat(item.displayValues().get("note").asText()).isEqualTo("visible");
    }

    private void update(List<String> fields) {
        service.updatePreference(
                INSTALLATION_ID,
                USER_ID,
                SCHEMA,
                new UpdatePluginAccountViewPreferenceRequest(fields)
        );
    }

    private PluginAccount account(String accountJson) {
        LocalDateTime now = LocalDateTime.of(2026, 8, 12, 10, 0);
        return new PluginAccount(1L, INSTALLATION_ID, "account-a", "Account A", accountJson, true, now, now, null, null);
    }
}
