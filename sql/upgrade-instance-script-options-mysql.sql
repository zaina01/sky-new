-- Apply once after upgrade-standalone-scripts-mysql.sql. No automatic legacy migration.
CREATE TABLE installation_script_options (
  installation_id VARCHAR(128) NOT NULL PRIMARY KEY,
  options_json JSON NOT NULL,
  updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3)
);
CREATE TABLE installation_script_variable (
  variable_id VARCHAR(36) NOT NULL PRIMARY KEY,
  installation_id VARCHAR(128) NOT NULL,
  name_key VARCHAR(128) NOT NULL,
  variable_json JSON NOT NULL,
  updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  UNIQUE KEY uk_script_variable_name(installation_id,name_key)
);
ALTER TABLE job_execution ADD COLUMN script_options_revision VARCHAR(32) NOT NULL DEFAULT '';
-- Old job_script_options remains intact. Nonempty legacy rows block their installation.
