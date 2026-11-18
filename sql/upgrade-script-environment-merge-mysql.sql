-- Apply once after upgrade-instance-script-options-mysql.sql, before updating the host.
-- Row IDs and ciphertext remain unchanged. Missing merge settings default to @ and no overrides.
ALTER TABLE installation_script_variable
    DROP INDEX uk_script_variable_name,
    ADD INDEX idx_script_variable_name (installation_id, name_key);
