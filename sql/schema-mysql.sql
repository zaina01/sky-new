CREATE DATABASE IF NOT EXISTS sky_new
  DEFAULT CHARACTER SET utf8mb4
  DEFAULT COLLATE utf8mb4_0900_ai_ci;

USE sky_new;

CREATE TABLE IF NOT EXISTS sky_department (
  department_id BIGINT NOT NULL AUTO_INCREMENT,
  parent_id BIGINT NULL,
  department_name VARCHAR(128) NOT NULL,
  sort_order INT NOT NULL DEFAULT 0,
  enabled BOOLEAN NOT NULL DEFAULT TRUE,
  deleted BOOLEAN NOT NULL DEFAULT FALSE,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (department_id),
  UNIQUE KEY uk_sky_department_parent_name (parent_id, department_name),
  KEY idx_sky_department_parent (parent_id, deleted, sort_order),
  CONSTRAINT fk_sky_department_parent FOREIGN KEY (parent_id) REFERENCES sky_department (department_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

INSERT IGNORE INTO sky_department
  (department_id, parent_id, department_name, sort_order, enabled, deleted)
VALUES (1, NULL, '默认部门', 0, TRUE, FALSE);

CREATE TABLE IF NOT EXISTS sky_user (
  user_id VARCHAR(64) NOT NULL,
  username VARCHAR(128) NOT NULL,
  password_hash VARCHAR(255) NOT NULL,
  email VARCHAR(100) NOT NULL,
  phone VARCHAR(32) NULL,
  avatar VARCHAR(512) NULL,
  department_id BIGINT NOT NULL DEFAULT 1,
  enabled BOOLEAN NOT NULL DEFAULT TRUE,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (user_id),
  UNIQUE KEY uk_sky_user_username (username),
  UNIQUE KEY uk_sky_user_email (email),
  UNIQUE KEY uk_sky_user_phone (phone),
  KEY idx_sky_user_department (department_id, enabled),
  CONSTRAINT fk_sky_user_department FOREIGN KEY (department_id) REFERENCES sky_department (department_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS sky_role (
  role_id BIGINT NOT NULL AUTO_INCREMENT,
  role_name VARCHAR(64) NOT NULL,
  description VARCHAR(255) NOT NULL DEFAULT '',
  data_scope VARCHAR(32) NOT NULL DEFAULT 'SELF',
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (role_id),
  UNIQUE KEY uk_sky_role_name (role_name),
  KEY idx_sky_role_data_scope (data_scope)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS sky_permission (
  permission_id BIGINT NOT NULL AUTO_INCREMENT,
  permission_name VARCHAR(128) NOT NULL,
  description VARCHAR(255) NOT NULL DEFAULT '',
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (permission_id),
  UNIQUE KEY uk_sky_permission_name (permission_name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS sky_user_role (
  user_id VARCHAR(64) NOT NULL,
  role_id BIGINT NOT NULL,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (user_id, role_id),
  CONSTRAINT fk_sky_user_role_user FOREIGN KEY (user_id) REFERENCES sky_user (user_id),
  CONSTRAINT fk_sky_user_role_role FOREIGN KEY (role_id) REFERENCES sky_role (role_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS sky_role_permission (
  role_id BIGINT NOT NULL,
  permission_id BIGINT NOT NULL,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (role_id, permission_id),
  CONSTRAINT fk_sky_role_permission_role FOREIGN KEY (role_id) REFERENCES sky_role (role_id),
  CONSTRAINT fk_sky_role_permission_permission FOREIGN KEY (permission_id) REFERENCES sky_permission (permission_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS sky_role_department (
  role_id BIGINT NOT NULL,
  department_id BIGINT NOT NULL,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (role_id, department_id),
  CONSTRAINT fk_sky_role_department_role FOREIGN KEY (role_id) REFERENCES sky_role (role_id),
  CONSTRAINT fk_sky_role_department_department FOREIGN KEY (department_id) REFERENCES sky_department (department_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

INSERT IGNORE INTO sky_role (role_name, description, data_scope) VALUES
  ('ADMIN', 'Initial administrator role', 'ALL');

INSERT IGNORE INTO sky_permission (permission_name, description) VALUES
  ('user:read:self', 'Read own user profile'),
  ('user:update:self', 'Update own user profile'),
  ('user:password:change:self', 'Change own password'),
  ('plugin:artifact:upload', 'Upload plugin definitions'),
  ('plugin:runtime:read', 'Read plugin installations'),
  ('plugin:runtime:manage', 'Manage plugin installations'),
  ('plugin:config:read', 'Read plugin installation configuration'),
  ('plugin:config:write', 'Write plugin installation configuration'),
  ('plugin:account:read', 'Read plugin installation accounts'),
  ('plugin:account:write', 'Write plugin installation accounts'),
  ('plugin:audit:read', 'Read plugin audit and service-call logs'),
  ('plugin:service-permission:read', 'Read plugin service-call permissions'),
  ('plugin:service-permission:write', 'Manage plugin service-call permissions'),
  ('plugin:notification-channel:bind', 'Bind channels to plugin installations'),
  ('system:user:read', 'Read system users'),
  ('system:user:write', 'Create and update system users'),
  ('system:user:delete', 'Delete system users'),
  ('system:user:password:reset', 'Reset system user passwords'),
  ('system:role:read', 'Read system roles'),
  ('system:role:write', 'Create and update system roles'),
  ('system:role:delete', 'Delete system roles'),
  ('system:permission:read', 'Read system permissions'),
  ('system:department:read', 'Read departments'),
  ('system:department:write', 'Create and update departments'),
  ('system:department:delete', 'Delete departments'),
  ('system:data-ownership:transfer', 'Transfer owned resources'),
  ('system:overview:read', 'Read system overview dashboard'),
  ('system:login-log:read', 'Read login logs'),
  ('system:log:read', 'Read operation logs'),
  ('system:retention:cleanup', 'Run retention cleanup'),
  ('system:setting:read', 'Read system settings'),
  ('system:setting:write', 'Update system settings'),
  ('notification:channel:read', 'Read notification channels'),
  ('notification:channel:write', 'Create and update notification channels'),
  ('notification:channel:test', 'Test notification channels'),
  ('notification:rule:read', 'Read notification rules'),
  ('notification:rule:write', 'Create and update notification rules'),
  ('notification:history:read', 'Read notification send history'),
  ('notification:history:retry', 'Retry failed notification sends'),
  ('job:read', 'Read scheduled jobs'),
  ('job:write', 'Create and update scheduled jobs'),
  ('job:delete', 'Delete scheduled jobs'),
  ('job:run', 'Run scheduled jobs manually'),
  ('job:execution:read', 'Read job execution history'),
  ('job:execution:cancel', 'Cancel running job executions'),
  ('job:notification:bind', 'Bind channels to jobs');

INSERT IGNORE INTO sky_role_permission (role_id, permission_id)
SELECT r.role_id, p.permission_id
FROM sky_role r
CROSS JOIN sky_permission p
WHERE r.role_name = 'ADMIN';

CREATE TABLE IF NOT EXISTS login_log (
  id BIGINT NOT NULL AUTO_INCREMENT,
  user_id VARCHAR(64) NULL,
  owner_dept_id BIGINT NULL,
  username VARCHAR(128) NULL,
  status VARCHAR(32) NOT NULL,
  login_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  device_type VARCHAR(32) NOT NULL DEFAULT 'UNKNOWN',
  login_ip VARCHAR(64) NULL,
  ip_type VARCHAR(32) NOT NULL DEFAULT 'UNKNOWN',
  user_agent VARCHAR(512) NULL,
  fail_reason VARCHAR(500) NULL,
  request_uri VARCHAR(512) NULL,
  request_method VARCHAR(16) NULL,
  remark VARCHAR(500) NULL,
  PRIMARY KEY (id),
  KEY idx_login_log_time (login_time),
  KEY idx_login_log_owner (owner_dept_id, user_id, login_time),
  KEY idx_login_log_username (username, login_time),
  KEY idx_login_log_status (status, login_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS system_log (
  id BIGINT NOT NULL AUTO_INCREMENT,
  module VARCHAR(64) NOT NULL,
  action VARCHAR(128) NOT NULL,
  level VARCHAR(16) NOT NULL,
  status VARCHAR(32) NOT NULL,
  operator_user_id VARCHAR(64) NULL,
  owner_dept_id BIGINT NULL,
  operator_name VARCHAR(128) NULL,
  source_type VARCHAR(16) NULL,
  request_uri VARCHAR(512) NULL,
  request_method VARCHAR(16) NULL,
  ip VARCHAR(64) NULL,
  user_agent VARCHAR(512) NULL,
  target_type VARCHAR(64) NULL,
  target_id VARCHAR(128) NULL,
  message VARCHAR(500) NULL,
  detail LONGTEXT NULL,
  remark VARCHAR(500) NULL,
  create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_system_log_time (create_time),
  KEY idx_system_log_module (module, create_time),
  KEY idx_system_log_owner (owner_dept_id, operator_user_id, create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS system_setting (
  setting_key VARCHAR(128) NOT NULL,
  category VARCHAR(64) NOT NULL,
  value_text LONGTEXT NOT NULL,
  value_type VARCHAR(32) NOT NULL,
  description VARCHAR(255) NOT NULL DEFAULT '',
  editable BOOLEAN NOT NULL DEFAULT TRUE,
  updated_by VARCHAR(128) NOT NULL DEFAULT '',
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (setting_key),
  KEY idx_system_setting_category (category)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS plugin_local_dependency_artifact (
  id BIGINT NOT NULL AUTO_INCREMENT,
  group_id VARCHAR(128) NOT NULL,
  artifact_id VARCHAR(128) NOT NULL,
  version VARCHAR(128) NOT NULL,
  classifier VARCHAR(128) NOT NULL DEFAULT '',
  extension VARCHAR(32) NOT NULL DEFAULT 'jar',
  coordinate VARCHAR(512) NOT NULL,
  relative_path VARCHAR(1024) NOT NULL,
  sha256 CHAR(64) NOT NULL,
  original_filename VARCHAR(255) NOT NULL DEFAULT '',
  size_bytes BIGINT NOT NULL DEFAULT 0,
  uploaded_by VARCHAR(128) NOT NULL DEFAULT '',
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_plugin_local_dependency_coord (group_id, artifact_id, version, classifier, extension),
  KEY idx_plugin_local_dependency_artifact (artifact_id, version)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS plugin_definition (
  plugin_id VARCHAR(128) NOT NULL,
  version VARCHAR(64) NOT NULL DEFAULT '',
  display_name VARCHAR(255) NOT NULL DEFAULT '',
  main_class VARCHAR(512) NOT NULL DEFAULT '',
  runtime_engine VARCHAR(16) NOT NULL DEFAULT 'java',
    execution_mode VARCHAR(16) NOT NULL DEFAULT 'plugin',
  entry_point VARCHAR(512) NOT NULL DEFAULT '',
  artifact_path VARCHAR(1024) NOT NULL DEFAULT '',
  artifact_sha256 CHAR(64) NOT NULL DEFAULT '',
  capabilities_json JSON NOT NULL,
  config_schema_json JSON NOT NULL,
  account_schema_json JSON NOT NULL,
  dependency_jars_json JSON NOT NULL,
  uploaded_by VARCHAR(128) NOT NULL DEFAULT '',
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (plugin_id),
  KEY idx_plugin_definition_updated (updated_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS plugin_installation (
  id BIGINT NOT NULL AUTO_INCREMENT,
  installation_id VARCHAR(64) NOT NULL,
  plugin_id VARCHAR(128) NOT NULL,
  instance_id VARCHAR(255) NOT NULL,
  installation_name VARCHAR(255) NOT NULL DEFAULT '',
  runtime_type VARCHAR(64) NOT NULL DEFAULT 'process',
  state VARCHAR(32) NOT NULL DEFAULT 'STOPPED',
  runner_host VARCHAR(255) NOT NULL DEFAULT '127.0.0.1',
  runner_port INT NULL,
  work_dir VARCHAR(1024) NULL,
  remote_debug_enabled BOOLEAN NOT NULL DEFAULT FALSE,
  remote_debug_port INT NOT NULL DEFAULT 5005,
  remote_debug_suspend BOOLEAN NOT NULL DEFAULT TRUE,
  owner_user_id VARCHAR(64) NOT NULL,
  owner_dept_id BIGINT NOT NULL,
  deleted BOOLEAN NOT NULL DEFAULT FALSE,
  started_at TIMESTAMP NULL,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_plugin_installation_id (installation_id),
  UNIQUE KEY uk_plugin_installation_instance (instance_id),
  KEY idx_plugin_installation_definition (plugin_id, deleted),
  KEY idx_plugin_installation_owner (owner_dept_id, owner_user_id, deleted),
  KEY idx_plugin_installation_state (state, deleted),
  CONSTRAINT fk_plugin_installation_definition FOREIGN KEY (plugin_id) REFERENCES plugin_definition (plugin_id),
  CONSTRAINT fk_plugin_installation_owner_user FOREIGN KEY (owner_user_id) REFERENCES sky_user (user_id),
  CONSTRAINT fk_plugin_installation_owner_dept FOREIGN KEY (owner_dept_id) REFERENCES sky_department (department_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS notification_channel (
  id BIGINT NOT NULL AUTO_INCREMENT,
  channel_id VARCHAR(128) NOT NULL,
  channel_type VARCHAR(32) NOT NULL,
  display_name VARCHAR(255) NOT NULL DEFAULT '',
  enabled BOOLEAN NOT NULL DEFAULT TRUE,
  config_json JSON NOT NULL,
  remark VARCHAR(500) NULL,
  owner_user_id VARCHAR(64) NOT NULL,
  owner_dept_id BIGINT NOT NULL,
  created_by VARCHAR(128) NOT NULL DEFAULT '',
  updated_by VARCHAR(128) NOT NULL DEFAULT '',
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_notification_channel_id (channel_id),
  KEY idx_notification_channel_owner (owner_dept_id, owner_user_id),
  KEY idx_notification_channel_type (channel_type, enabled),
  CONSTRAINT fk_notification_channel_owner_user FOREIGN KEY (owner_user_id) REFERENCES sky_user (user_id),
  CONSTRAINT fk_notification_channel_owner_dept FOREIGN KEY (owner_dept_id) REFERENCES sky_department (department_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS plugin_installation_channel (
  installation_id VARCHAR(64) NOT NULL,
  channel_id VARCHAR(128) NOT NULL,
  created_by VARCHAR(128) NOT NULL DEFAULT '',
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (installation_id, channel_id),
  KEY idx_plugin_installation_channel_channel (channel_id, installation_id),
  CONSTRAINT fk_plugin_installation_channel_installation FOREIGN KEY (installation_id) REFERENCES plugin_installation (installation_id),
  CONSTRAINT fk_plugin_installation_channel_channel FOREIGN KEY (channel_id) REFERENCES notification_channel (channel_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS job (
  id BIGINT NOT NULL AUTO_INCREMENT,
  job_name VARCHAR(255) NOT NULL,
  plugin_installation_id VARCHAR(64) NOT NULL,
  cron_expression VARCHAR(128) NOT NULL,
  job_status VARCHAR(32) NOT NULL DEFAULT 'ENABLED',
  misfire_policy VARCHAR(32) NOT NULL DEFAULT 'DEFAULT',
  concurrent BOOLEAN NOT NULL DEFAULT TRUE,
  input_json JSON NOT NULL,
  timeout_millis BIGINT NOT NULL DEFAULT 120000,
  executed_status VARCHAR(32) NOT NULL DEFAULT 'IDLE',
  remark VARCHAR(500) NULL,
  owner_user_id VARCHAR(64) NOT NULL,
  owner_dept_id BIGINT NOT NULL,
  created_by VARCHAR(128) NOT NULL DEFAULT '',
  updated_by VARCHAR(128) NOT NULL DEFAULT '',
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_job_installation (plugin_installation_id),
  KEY idx_job_owner (owner_dept_id, owner_user_id),
  KEY idx_job_status (job_status),
  KEY idx_job_executed_status (executed_status),
  CONSTRAINT fk_job_installation FOREIGN KEY (plugin_installation_id) REFERENCES plugin_installation (installation_id),
  CONSTRAINT fk_job_owner_user FOREIGN KEY (owner_user_id) REFERENCES sky_user (user_id),
  CONSTRAINT fk_job_owner_dept FOREIGN KEY (owner_dept_id) REFERENCES sky_department (department_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS job_notification_channel (
  job_id BIGINT NOT NULL,
  channel_id VARCHAR(128) NOT NULL,
  created_by VARCHAR(128) NOT NULL DEFAULT '',
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (job_id, channel_id),
  KEY idx_job_notification_channel_channel (channel_id, job_id),
  CONSTRAINT fk_job_notification_channel_job FOREIGN KEY (job_id) REFERENCES job (id) ON DELETE CASCADE,
  CONSTRAINT fk_job_notification_channel_channel FOREIGN KEY (channel_id) REFERENCES notification_channel (channel_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS job_execution (
  runtime_engine VARCHAR(16) NOT NULL DEFAULT 'java',
    execution_mode VARCHAR(16) NOT NULL DEFAULT 'plugin',
  artifact_sha256 VARCHAR(64) NOT NULL DEFAULT '',
  environment_revision VARCHAR(64) NOT NULL DEFAULT '',
  interpreter_version VARCHAR(128) NOT NULL DEFAULT '',
  script_options_revision VARCHAR(32) NOT NULL DEFAULT '',
  id BIGINT NOT NULL AUTO_INCREMENT,
  job_id BIGINT NOT NULL,
  plugin_installation_id VARCHAR(64) NOT NULL,
  plugin_id VARCHAR(128) NOT NULL,
  instance_id VARCHAR(255) NOT NULL DEFAULT '',
  request_id VARCHAR(128) NOT NULL DEFAULT '',
  status VARCHAR(32) NOT NULL,
  trigger_type VARCHAR(32) NOT NULL,
  job_name VARCHAR(255) NOT NULL DEFAULT '',
  started_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  finished_at TIMESTAMP(3) NULL,
  duration_millis BIGINT NULL,
  message VARCHAR(1000) NULL,
  result_json JSON NULL,
  error_type VARCHAR(512) NULL,
  error_message TEXT NULL,
  runner_log_path VARCHAR(1024) NULL,
  owner_user_id VARCHAR(64) NULL,
  owner_dept_id BIGINT NULL,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_job_execution_job (job_id, started_at),
  KEY idx_job_execution_installation (plugin_installation_id, started_at),
  KEY idx_job_execution_plugin (plugin_id, started_at),
  KEY idx_job_execution_owner (owner_dept_id, owner_user_id, started_at),
  KEY idx_job_execution_status (status, started_at),
  KEY idx_job_execution_request (plugin_installation_id, request_id),
  CONSTRAINT fk_job_execution_job FOREIGN KEY (job_id) REFERENCES job (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS notification_rule (
  id BIGINT NOT NULL AUTO_INCREMENT,
  rule_id VARCHAR(128) NOT NULL,
  display_name VARCHAR(255) NOT NULL DEFAULT '',
  enabled BOOLEAN NOT NULL DEFAULT TRUE,
  event_type VARCHAR(64) NOT NULL,
  plugin_installation_id VARCHAR(64) NULL,
  job_id BIGINT NULL,
  severity VARCHAR(32) NOT NULL DEFAULT '',
  remark VARCHAR(500) NULL,
  owner_user_id VARCHAR(64) NOT NULL,
  owner_dept_id BIGINT NOT NULL,
  created_by VARCHAR(128) NOT NULL DEFAULT '',
  updated_by VARCHAR(128) NOT NULL DEFAULT '',
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_notification_rule_id (rule_id),
  KEY idx_notification_rule_match (enabled, event_type, plugin_installation_id, job_id, severity),
  KEY idx_notification_rule_owner (owner_dept_id, owner_user_id),
  CONSTRAINT fk_notification_rule_installation FOREIGN KEY (plugin_installation_id) REFERENCES plugin_installation (installation_id),
  CONSTRAINT fk_notification_rule_job FOREIGN KEY (job_id) REFERENCES job (id) ON DELETE SET NULL,
  CONSTRAINT fk_notification_rule_owner_user FOREIGN KEY (owner_user_id) REFERENCES sky_user (user_id),
  CONSTRAINT fk_notification_rule_owner_dept FOREIGN KEY (owner_dept_id) REFERENCES sky_department (department_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS notification_rule_channel (
  rule_id VARCHAR(128) NOT NULL,
  channel_id VARCHAR(128) NOT NULL,
  created_by VARCHAR(128) NOT NULL DEFAULT '',
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (rule_id, channel_id),
  KEY idx_notification_rule_channel_channel (channel_id, rule_id),
  CONSTRAINT fk_notification_rule_channel_rule FOREIGN KEY (rule_id) REFERENCES notification_rule (rule_id) ON DELETE CASCADE,
  CONSTRAINT fk_notification_rule_channel_channel FOREIGN KEY (channel_id) REFERENCES notification_channel (channel_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS notification_send_history (
  id BIGINT NOT NULL AUTO_INCREMENT,
  request_id VARCHAR(128) NOT NULL DEFAULT '',
  source_type VARCHAR(32) NOT NULL,
  installation_id VARCHAR(64) NOT NULL DEFAULT '',
  plugin_id VARCHAR(128) NOT NULL DEFAULT '',
  instance_id VARCHAR(255) NOT NULL DEFAULT '',
  job_id VARCHAR(64) NOT NULL DEFAULT '',
  execution_id BIGINT NULL,
  source_owner_user_id VARCHAR(64) NULL,
  source_owner_dept_id BIGINT NULL,
  channel_owner_user_id VARCHAR(64) NULL,
  channel_owner_dept_id BIGINT NULL,
  channel_id VARCHAR(128) NOT NULL,
  channel_type VARCHAR(32) NOT NULL,
  title VARCHAR(255) NOT NULL DEFAULT '',
  severity VARCHAR(32) NOT NULL DEFAULT '',
  content_preview VARCHAR(500) NOT NULL DEFAULT '',
  content_payload LONGTEXT NULL,
  payload_bytes INT NOT NULL DEFAULT 0,
  labels_json JSON NULL,
  status VARCHAR(32) NOT NULL,
  attempt_count INT NOT NULL DEFAULT 0,
  max_attempts INT NOT NULL DEFAULT 3,
  next_retry_at TIMESTAMP(3) NULL,
  first_attempt_at TIMESTAMP(3) NULL,
  last_attempt_at TIMESTAMP(3) NULL,
  locked_at TIMESTAMP(3) NULL,
  locked_by VARCHAR(128) NULL,
  manual_retry_count INT NOT NULL DEFAULT 0,
  last_manual_retry_at TIMESTAMP(3) NULL,
  last_manual_retry_by VARCHAR(128) NULL,
  error_type VARCHAR(512) NULL,
  error_message VARCHAR(1000) NULL,
  provider_status_code VARCHAR(64) NULL,
  provider_message VARCHAR(1000) NULL,
  created_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  sent_at TIMESTAMP(3) NULL,
  updated_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  KEY idx_notification_history_created (created_at),
  KEY idx_notification_history_channel (channel_id, created_at),
  KEY idx_notification_history_installation (installation_id, created_at),
  KEY idx_notification_history_source_owner (source_owner_dept_id, source_owner_user_id, created_at),
  KEY idx_notification_history_channel_owner (channel_owner_dept_id, channel_owner_user_id, created_at),
  KEY idx_notification_queue_due (status, next_retry_at, id),
  KEY idx_notification_history_request_channel (request_id, channel_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS plugin_service_permission (
  id BIGINT NOT NULL AUTO_INCREMENT,
  caller_installation_id VARCHAR(64) NOT NULL,
  target_installation_id VARCHAR(64) NOT NULL,
  operation VARCHAR(128) NOT NULL,
  enabled BOOLEAN NOT NULL DEFAULT TRUE,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_plugin_service_permission (caller_installation_id, target_installation_id, operation),
  KEY idx_plugin_service_permission_target (target_installation_id, operation, enabled),
  CONSTRAINT fk_plugin_service_permission_caller FOREIGN KEY (caller_installation_id) REFERENCES plugin_installation (installation_id),
  CONSTRAINT fk_plugin_service_permission_target FOREIGN KEY (target_installation_id) REFERENCES plugin_installation (installation_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS plugin_service_call_log (
  id BIGINT NOT NULL AUTO_INCREMENT,
  job_id BIGINT NULL,
  execution_id BIGINT NULL,
  caller_installation_id VARCHAR(64) NOT NULL,
  caller_plugin_id VARCHAR(128) NOT NULL,
  caller_owner_user_id VARCHAR(64) NULL,
  caller_owner_dept_id BIGINT NULL,
  caller_instance_id VARCHAR(255) NOT NULL,
  caller_request_id VARCHAR(128) NOT NULL DEFAULT '',
  target_installation_id VARCHAR(64) NULL,
  target_plugin_id VARCHAR(128) NULL,
  target_owner_user_id VARCHAR(64) NULL,
  target_owner_dept_id BIGINT NULL,
  target_instance_id VARCHAR(255) NOT NULL DEFAULT '',
  operation VARCHAR(128) NOT NULL DEFAULT '',
  service_request_id VARCHAR(64) NOT NULL,
  target_runner_log_path VARCHAR(1024) NOT NULL DEFAULT '',
  timeout_millis BIGINT NOT NULL,
  duration_millis BIGINT NOT NULL,
  request_bytes INT NOT NULL DEFAULT 0,
  response_bytes INT NOT NULL DEFAULT 0,
  success BOOLEAN NOT NULL DEFAULT FALSE,
  error_type VARCHAR(255) NOT NULL DEFAULT '',
  error_message VARCHAR(1024) NOT NULL DEFAULT '',
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_plugin_service_call_request (service_request_id),
  KEY idx_plugin_service_call_job (job_id, id),
  KEY idx_plugin_service_call_caller (caller_installation_id, id),
  KEY idx_plugin_service_call_target (target_installation_id, id),
  KEY idx_plugin_service_call_caller_owner (caller_owner_dept_id, caller_owner_user_id, id),
  KEY idx_plugin_service_call_target_owner (target_owner_dept_id, target_owner_user_id, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS plugin_config (
  id BIGINT NOT NULL AUTO_INCREMENT,
  installation_id VARCHAR(64) NOT NULL,
  config_json JSON NOT NULL,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_plugin_config_installation (installation_id),
  CONSTRAINT fk_plugin_config_installation FOREIGN KEY (installation_id) REFERENCES plugin_installation (installation_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS plugin_account_group (
  group_id BIGINT NOT NULL AUTO_INCREMENT,
  installation_id VARCHAR(64) NOT NULL,
  name VARCHAR(128) NOT NULL,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (group_id),
  UNIQUE KEY uk_plugin_account_group_installation_id (installation_id, group_id),
  UNIQUE KEY uk_plugin_account_group_name (installation_id, name),
  CONSTRAINT fk_plugin_account_group_installation FOREIGN KEY (installation_id) REFERENCES plugin_installation (installation_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS plugin_account (
  id BIGINT NOT NULL AUTO_INCREMENT,
  installation_id VARCHAR(64) NOT NULL,
  account_id VARCHAR(128) NOT NULL,
  group_id BIGINT NULL,
  display_name VARCHAR(255) NOT NULL DEFAULT '',
  account_json JSON NOT NULL,
  enabled BOOLEAN NOT NULL DEFAULT TRUE,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_plugin_account_installation_account (installation_id, account_id),
  KEY idx_plugin_account_enabled (installation_id, enabled),
  KEY idx_plugin_account_created (installation_id, created_at, id),
  KEY idx_plugin_account_group_created (installation_id, group_id, created_at, id),
  CONSTRAINT fk_plugin_account_group FOREIGN KEY (installation_id, group_id) REFERENCES plugin_account_group (installation_id, group_id),
  CONSTRAINT fk_plugin_account_installation FOREIGN KEY (installation_id) REFERENCES plugin_installation (installation_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS plugin_account_list_preference (
  user_id VARCHAR(64) NOT NULL,
  installation_id VARCHAR(64) NOT NULL,
  selected_fields_json JSON NOT NULL,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (user_id, installation_id),
  KEY idx_plugin_account_list_preference_installation (installation_id),
  CONSTRAINT fk_plugin_account_list_preference_user FOREIGN KEY (user_id) REFERENCES sky_user (user_id) ON DELETE CASCADE,
  CONSTRAINT fk_plugin_account_list_preference_installation FOREIGN KEY (installation_id) REFERENCES plugin_installation (installation_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS plugin_data (
  id BIGINT NOT NULL AUTO_INCREMENT,
  installation_id VARCHAR(64) NOT NULL,
  collection_name VARCHAR(128) NOT NULL,
  data_key VARCHAR(255) NOT NULL,
  data_json JSON NOT NULL,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_plugin_data_key (installation_id, collection_name, data_key),
  KEY idx_plugin_data_collection (installation_id, collection_name),
  CONSTRAINT fk_plugin_data_installation FOREIGN KEY (installation_id) REFERENCES plugin_installation (installation_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS plugin_task_log (
  id BIGINT NOT NULL AUTO_INCREMENT,
  installation_id VARCHAR(64) NOT NULL,
  plugin_id VARCHAR(128) NOT NULL,
  instance_id VARCHAR(255) NOT NULL,
  job_id VARCHAR(128) NOT NULL DEFAULT '',
  request_id VARCHAR(128) NOT NULL DEFAULT '',
  level VARCHAR(16) NOT NULL,
  message TEXT NOT NULL,
  sequence_no BIGINT NOT NULL DEFAULT 0,
  event_time TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_plugin_task_log_request (installation_id, request_id, sequence_no),
  KEY idx_plugin_task_log_job (installation_id, job_id, event_time),
  KEY idx_plugin_task_log_event_time (event_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS plugin_audit_log (
  id BIGINT NOT NULL AUTO_INCREMENT,
  installation_id VARCHAR(64) NOT NULL DEFAULT '',
  plugin_id VARCHAR(128) NOT NULL DEFAULT '',
  instance_id VARCHAR(255) NOT NULL DEFAULT '',
  owner_user_id VARCHAR(64) NULL,
  owner_dept_id BIGINT NULL,
  actor VARCHAR(128) NOT NULL DEFAULT '',
  action VARCHAR(64) NOT NULL,
  detail_json JSON NOT NULL,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_plugin_audit_installation (installation_id, created_at),
  KEY idx_plugin_audit_owner (owner_dept_id, owner_user_id, created_at),
  KEY idx_plugin_audit_plugin (plugin_id, created_at),
  KEY idx_plugin_audit_action (action, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS script_environment_document (
  document_key VARCHAR(192) NOT NULL PRIMARY KEY,
  document_json JSON NOT NULL,
  updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3)
);

CREATE TABLE IF NOT EXISTS job_script_options (
 job_id BIGINT NOT NULL PRIMARY KEY,
 options_json JSON NOT NULL,
 updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3)
);

CREATE TABLE IF NOT EXISTS installation_script_options (
  installation_id VARCHAR(128) NOT NULL PRIMARY KEY,
  options_json JSON NOT NULL,
  updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3)
);
CREATE TABLE IF NOT EXISTS installation_script_variable (
  variable_id VARCHAR(36) NOT NULL PRIMARY KEY,
  installation_id VARCHAR(128) NOT NULL,
  name_key VARCHAR(128) NOT NULL,
  variable_json JSON NOT NULL,
  updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  KEY idx_script_variable_name(installation_id,name_key)
);
