package com.sky.job.system;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class DataPermissionSchemaMigrationTest {
    @Test
    void freshSchemaContainsDepartmentOwnershipAndInstallationIsolationModel() throws Exception {
        String schema = sql("schema-mysql.sql");

        assertThat(schema)
                .contains("VALUES (1, NULL, '默认部门'")
                .contains("data_scope VARCHAR(32) NOT NULL DEFAULT 'SELF'")
                .contains("('ADMIN', 'Initial administrator role', 'ALL')")
                .contains("CREATE TABLE IF NOT EXISTS sky_role_department")
                .contains("CREATE TABLE IF NOT EXISTS plugin_definition")
                .contains("CREATE TABLE IF NOT EXISTS plugin_installation")
                .contains("UNIQUE KEY uk_plugin_account_installation_account (installation_id, account_id)")
                .contains("CREATE TABLE IF NOT EXISTS plugin_account_list_preference")
                .contains("PRIMARY KEY (user_id, installation_id)")
                .contains("fk_plugin_account_list_preference_user FOREIGN KEY (user_id) REFERENCES sky_user (user_id) ON DELETE CASCADE")
                .contains("CREATE TABLE IF NOT EXISTS plugin_installation_channel")
                .contains("CREATE TABLE IF NOT EXISTS job_notification_channel")
                .contains("CREATE TABLE IF NOT EXISTS notification_rule_channel")
                .contains("source_owner_user_id VARCHAR(64) NULL")
                .contains("channel_owner_user_id VARCHAR(64) NULL")
                .contains("caller_owner_user_id VARCHAR(64) NULL")
                .contains("target_owner_user_id VARCHAR(64) NULL")
                .contains("KEY idx_plugin_audit_owner (owner_dept_id, owner_user_id, created_at)")
                .doesNotContain("CREATE TABLE IF NOT EXISTS plugin_runtime");
    }

    @Test
    void accountListPreferenceUpgradeIsIdempotentAndRequiresNoBackfill() throws Exception {
        String migration = sql("add-plugin-account-list-preference-mysql.sql");

        assertThat(migration)
                .contains("CREATE TABLE IF NOT EXISTS plugin_account_list_preference")
                .contains("PRIMARY KEY (user_id, installation_id)")
                .contains("fk_plugin_account_list_preference_user FOREIGN KEY (user_id) REFERENCES sky_user (user_id) ON DELETE CASCADE")
                .contains("fk_plugin_account_list_preference_installation FOREIGN KEY (installation_id) REFERENCES plugin_installation (installation_id)")
                .doesNotContain("INSERT INTO plugin_account_list_preference");
    }

    @Test
    void upgradePreservesCompatibilityAndMigratesEveryLegacyPluginToOneInstallation() throws Exception {
        String migration = sql("upgrade-data-scope-plugin-installation-mysql.sql");

        assertThat(migration)
                .contains("UPDATE sky_role SET data_scope='ALL'")
                .contains("VALUES (1, NULL, '默认部门'")
                .contains("fk_sky_department_parent FOREIGN KEY (parent_id) REFERENCES sky_department (department_id)")
                .contains("ADD KEY idx_sky_role_data_scope (data_scope)")
                .contains("CREATE TABLE migration_plugin_installation_map")
                .contains("SELECT plugin_id, UUID() FROM plugin_runtime")
                .contains("JOIN migration_plugin_installation_map m ON m.plugin_id=r.plugin_id")
                .contains("CASE WHEN h.job_id REGEXP '^[0-9]+$' THEN h.job_id ELSE NULL END")
                .contains("MODIFY instance_id VARCHAR(255) NOT NULL DEFAULT ''")
                .contains("ADD KEY idx_job_execution_request (plugin_installation_id, request_id)")
                .contains("fk_job_execution_job FOREIGN KEY (job_id) REFERENCES job (id) ON DELETE CASCADE")
                .contains("ADD KEY idx_plugin_audit_owner (owner_dept_id, owner_user_id, created_at)")
                .contains("DROP TABLE plugin_runtime")
                .contains("DROP TABLE migration_plugin_installation_map")
                .contains("SET FOREIGN_KEY_CHECKS = COALESCE(@OLD_FOREIGN_KEY_CHECKS, 1)");
    }

    @Test
    void migrationBuildsPushWhitelistsOnlyFromActualPluginSendHistory() throws Exception {
        String migration = sql("upgrade-data-scope-plugin-installation-mysql.sql");
        int start = migration.indexOf("-- Plugin sends with a job establish both whitelist levels");
        int end = migration.indexOf("ALTER TABLE plugin_config", start);

        assertThat(start).isGreaterThanOrEqualTo(0);
        assertThat(end).isGreaterThan(start);
        String whitelistMigration = migration.substring(start, end);
        assertThat(whitelistMigration)
                .contains("INSERT IGNORE INTO plugin_installation_channel")
                .contains("INSERT IGNORE INTO job_notification_channel")
                .contains("h.source_type='PLUGIN_RUNTIME'")
                .contains("j.plugin_installation_id=h.installation_id")
                .doesNotContain("notification_rule_channel")
                .doesNotContain("notification_rule r");
    }

    @Test
    void ruleChannelsAreMigratedToTheirOwnRelationWithoutExpandingPushPool() throws Exception {
        String migration = sql("upgrade-data-scope-plugin-installation-mysql.sql");

        assertThat(migration)
                .contains("INSERT IGNORE INTO notification_rule_channel")
                .contains("COALESCE(r.channel_ids_json, JSON_ARRAY())")
                .contains("DROP COLUMN channel_ids_json");
    }

    private String sql(String filename) throws IOException {
        Path directory = findSqlDirectory();
        return Files.readString(directory.resolve(filename), StandardCharsets.UTF_8);
    }

    private Path findSqlDirectory() {
        Path current = Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize();
        while (current != null) {
            Path candidate = current.resolve("sql");
            if (Files.isRegularFile(candidate.resolve("schema-mysql.sql"))) {
                return candidate;
            }
            current = current.getParent();
        }
        throw new IllegalStateException("Repository sql directory not found");
    }
}
