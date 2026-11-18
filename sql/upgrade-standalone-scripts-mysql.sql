-- Apply after upgrade-script-plugins-mysql.sql, once, before upgrading the host.
ALTER TABLE plugin_definition ADD COLUMN execution_mode VARCHAR(16) NOT NULL DEFAULT 'plugin';
ALTER TABLE job_execution ADD COLUMN execution_mode VARCHAR(16) NOT NULL DEFAULT 'plugin';
CREATE TABLE job_script_options (
    job_id BIGINT NOT NULL PRIMARY KEY,
    options_json JSON NOT NULL,
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3)
);
