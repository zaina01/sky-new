CREATE DATABASE IF NOT EXISTS sky_new
  DEFAULT CHARACTER SET utf8mb4
  DEFAULT COLLATE utf8mb4_0900_ai_ci;

USE sky_new;

CREATE TABLE IF NOT EXISTS sky_user (
  user_id VARCHAR(64) NOT NULL,
  username VARCHAR(128) NOT NULL,
  password_hash VARCHAR(255) NOT NULL,
  email VARCHAR(100) NOT NULL,
  phone VARCHAR(32) DEFAULT NULL,
  avatar VARCHAR(512) DEFAULT NULL,
  enabled BOOLEAN NOT NULL DEFAULT TRUE,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (user_id),
  UNIQUE KEY uk_sky_user_username (username),
  UNIQUE KEY uk_sky_user_email (email),
  UNIQUE KEY uk_sky_user_phone (phone)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS sky_role (
  role_id BIGINT NOT NULL AUTO_INCREMENT,
  role_name VARCHAR(64) NOT NULL,
  description VARCHAR(255) NOT NULL DEFAULT '',
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (role_id),
  UNIQUE KEY uk_sky_role_name (role_name)
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

INSERT IGNORE INTO sky_role (role_name, description) VALUES
  ('ADMIN', 'Initial administrator role');

INSERT IGNORE INTO sky_permission (permission_name, description) VALUES
  ('user:read:self', 'Read own user profile'),
  ('user:update:self', 'Update own user profile'),
  ('user:password:change:self', 'Change own password'),
  ('plugin:artifact:upload', 'Upload plugin artifacts'),
  ('plugin:runtime:read', 'Read plugin runtime metadata'),
  ('plugin:runtime:manage', 'Manage plugin runtime lifecycle'),
  ('plugin:config:read', 'Read plugin configuration'),
  ('plugin:config:write', 'Write plugin configuration'),
  ('plugin:account:read', 'Read plugin account data'),
  ('plugin:account:write', 'Write plugin account data'),
  ('plugin:audit:read', 'Read plugin audit logs'),
  ('plugin:service-permission:read', 'Read plugin service call permissions'),
  ('plugin:service-permission:write', 'Create and update plugin service call permissions'),
  ('system:user:read', 'Read system users'),
  ('system:user:write', 'Create and update system users'),
  ('system:user:delete', 'Delete system users'),
  ('system:user:password:reset', 'Reset system user passwords'),
  ('system:role:read', 'Read system roles'),
  ('system:role:write', 'Create and update system roles'),
  ('system:role:delete', 'Delete system roles'),
  ('system:permission:read', 'Read system permissions'),
  ('system:overview:read', 'Read system overview dashboard'),
  ('system:login-log:read', 'Read system login logs'),
  ('system:log:read', 'Read system operation logs'),
  ('system:retention:cleanup', 'Run retention cleanup'),
  ('system:setting:read', 'Read system settings'),
  ('system:setting:write', 'Update system settings'),
  ('notification:channel:read', 'Read notification channels'),
  ('notification:channel:write', 'Create and update notification channels'),
  ('notification:channel:test', 'Test notification channels'),
  ('notification:rule:read', 'Read notification rules'),
  ('notification:rule:write', 'Create and update notification rules'),
  ('notification:history:read', 'Read notification send history'),
  ('notification:history:retry', 'Retry failed notification send history'),
  ('job:read', 'Read scheduled jobs'),
  ('job:write', 'Create and update scheduled jobs'),
  ('job:delete', 'Delete scheduled jobs'),
  ('job:run', 'Run scheduled jobs manually'),
  ('job:execution:read', 'Read job execution history'),
  ('job:execution:cancel', 'Cancel running job executions');

INSERT IGNORE INTO sky_role_permission (role_id, permission_id)
SELECT r.role_id, p.permission_id
FROM sky_role r
JOIN sky_permission p
WHERE r.role_name = 'ADMIN';

CREATE TABLE IF NOT EXISTS login_log (
  id BIGINT NOT NULL AUTO_INCREMENT,
  user_id VARCHAR(64) DEFAULT NULL,
  username VARCHAR(128) DEFAULT NULL,
  status VARCHAR(32) NOT NULL,
  login_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  device_type VARCHAR(32) NOT NULL DEFAULT 'UNKNOWN',
  login_ip VARCHAR(64) DEFAULT NULL,
  ip_type VARCHAR(32) NOT NULL DEFAULT 'UNKNOWN',
  user_agent VARCHAR(512) DEFAULT NULL,
  fail_reason VARCHAR(500) DEFAULT NULL,
  request_uri VARCHAR(512) DEFAULT NULL,
  request_method VARCHAR(16) DEFAULT NULL,
  remark VARCHAR(500) DEFAULT NULL,
  PRIMARY KEY (id),
  KEY idx_login_log_login_time (login_time),
  KEY idx_login_log_user_time (user_id, login_time),
  KEY idx_login_log_username_time (username, login_time),
  KEY idx_login_log_status_time (status, login_time),
  KEY idx_login_log_ip_time (login_ip, login_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS system_log (
  id BIGINT NOT NULL AUTO_INCREMENT,
  module VARCHAR(64) NOT NULL,
  action VARCHAR(128) NOT NULL,
  level VARCHAR(16) NOT NULL,
  status VARCHAR(32) NOT NULL,
  operator_user_id VARCHAR(64) DEFAULT NULL,
  operator_name VARCHAR(128) DEFAULT NULL,
  source_type VARCHAR(16) DEFAULT NULL,
  request_uri VARCHAR(512) DEFAULT NULL,
  request_method VARCHAR(16) DEFAULT NULL,
  ip VARCHAR(64) DEFAULT NULL,
  user_agent VARCHAR(512) DEFAULT NULL,
  target_type VARCHAR(64) DEFAULT NULL,
  target_id VARCHAR(64) DEFAULT NULL,
  message VARCHAR(500) DEFAULT NULL,
  detail LONGTEXT DEFAULT NULL,
  remark VARCHAR(500) DEFAULT NULL,
  create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_system_log_create_time (create_time),
  KEY idx_system_log_module_create_time (module, create_time),
  KEY idx_system_log_level_create_time (level, create_time),
  KEY idx_system_log_operator_create_time (operator_user_id, create_time)
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

CREATE TABLE IF NOT EXISTS notification_channel (
  id BIGINT NOT NULL AUTO_INCREMENT,
  channel_id VARCHAR(128) NOT NULL,
  channel_type VARCHAR(32) NOT NULL,
  display_name VARCHAR(255) NOT NULL DEFAULT '',
  enabled BOOLEAN NOT NULL DEFAULT TRUE,
  config_json JSON NOT NULL,
  remark VARCHAR(500) DEFAULT NULL,
  created_by VARCHAR(128) NOT NULL DEFAULT '',
  updated_by VARCHAR(128) NOT NULL DEFAULT '',
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_notification_channel_id (channel_id),
  KEY idx_notification_channel_type_enabled (channel_type, enabled)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS notification_rule (
  id BIGINT NOT NULL AUTO_INCREMENT,
  rule_id VARCHAR(128) NOT NULL,
  display_name VARCHAR(255) NOT NULL DEFAULT '',
  enabled BOOLEAN NOT NULL DEFAULT TRUE,
  event_type VARCHAR(64) NOT NULL,
  plugin_id VARCHAR(128) NOT NULL DEFAULT '',
  job_id VARCHAR(64) NOT NULL DEFAULT '',
  severity VARCHAR(32) NOT NULL DEFAULT '',
  channel_ids_json JSON NOT NULL,
  remark VARCHAR(500) DEFAULT NULL,
  created_by VARCHAR(128) NOT NULL DEFAULT '',
  updated_by VARCHAR(128) NOT NULL DEFAULT '',
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_notification_rule_id (rule_id),
  KEY idx_notification_rule_match (enabled, event_type, plugin_id, job_id, severity),
  KEY idx_notification_rule_created (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS notification_send_history (
  id BIGINT NOT NULL AUTO_INCREMENT,
  request_id VARCHAR(128) NOT NULL DEFAULT '',
  source_type VARCHAR(32) NOT NULL,
  plugin_id VARCHAR(128) NOT NULL DEFAULT '',
  instance_id VARCHAR(128) NOT NULL DEFAULT '',
  job_id VARCHAR(64) NOT NULL DEFAULT '',
  execution_id BIGINT NULL,
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
  error_type VARCHAR(512) DEFAULT NULL,
  error_message VARCHAR(1000) DEFAULT NULL,
  provider_status_code VARCHAR(64) DEFAULT NULL,
  provider_message VARCHAR(1000) DEFAULT NULL,
  created_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  sent_at TIMESTAMP(3) NULL,
  updated_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  KEY idx_notification_history_created (created_at),
  KEY idx_notification_history_channel (channel_id, created_at),
  KEY idx_notification_history_source (source_type, created_at),
  KEY idx_notification_history_plugin (plugin_id, created_at),
  KEY idx_notification_history_execution (execution_id),
  KEY idx_notification_queue_due (status, next_retry_at, id),
  KEY idx_notification_queue_locked (status, locked_at),
  KEY idx_notification_history_request_channel (request_id, channel_id),
  KEY idx_notification_history_status_created (status, created_at)
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
  KEY idx_plugin_local_dependency_artifact (artifact_id, version),
  KEY idx_plugin_local_dependency_updated (updated_at, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS plugin_runtime (
  id BIGINT NOT NULL AUTO_INCREMENT,
  plugin_id VARCHAR(128) NOT NULL,
  instance_id VARCHAR(128) NOT NULL,
  version VARCHAR(64) NOT NULL DEFAULT '',
  display_name VARCHAR(255) NOT NULL DEFAULT '',
  main_class VARCHAR(512) NOT NULL DEFAULT '',
  artifact_path VARCHAR(1024) NOT NULL DEFAULT '',
  artifact_sha256 CHAR(64) NOT NULL DEFAULT '',
  runtime_type VARCHAR(64) NOT NULL DEFAULT 'process',
  state VARCHAR(32) NOT NULL DEFAULT 'STOPPED',
  runner_host VARCHAR(255) NOT NULL DEFAULT '127.0.0.1',
  runner_port INT NULL,
  work_dir VARCHAR(1024) NOT NULL DEFAULT '',
  capabilities_json JSON NULL,
  config_schema_json JSON NULL,
  account_schema_json JSON NULL,
  dependency_jars_json JSON NULL,
  remote_debug_enabled BOOLEAN NOT NULL DEFAULT FALSE,
  remote_debug_port INT NOT NULL DEFAULT 5005,
  remote_debug_suspend BOOLEAN NOT NULL DEFAULT TRUE,
  started_at TIMESTAMP NULL,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_plugin_runtime_plugin_id (plugin_id),
  UNIQUE KEY uk_plugin_runtime_instance_id (instance_id),
  KEY idx_plugin_runtime_state (state)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS plugin_service_permission (
  id BIGINT NOT NULL AUTO_INCREMENT,
  caller_plugin_id VARCHAR(128) NOT NULL,
  target_plugin_id VARCHAR(128) NOT NULL,
  operation VARCHAR(128) NOT NULL,
  enabled BOOLEAN NOT NULL DEFAULT TRUE,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_plugin_service_permission (caller_plugin_id, target_plugin_id, operation),
  KEY idx_plugin_service_permission_target (target_plugin_id, operation, enabled),
  CONSTRAINT fk_plugin_service_permission_caller FOREIGN KEY (caller_plugin_id) REFERENCES plugin_runtime (plugin_id),
  CONSTRAINT fk_plugin_service_permission_target FOREIGN KEY (target_plugin_id) REFERENCES plugin_runtime (plugin_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS plugin_service_call_log (
  id BIGINT NOT NULL AUTO_INCREMENT,
  job_id BIGINT NULL,
  caller_plugin_id VARCHAR(128) NOT NULL,
  caller_instance_id VARCHAR(255) NOT NULL,
  caller_request_id VARCHAR(128) NOT NULL,
  target_plugin_id VARCHAR(128) NOT NULL,
  target_instance_id VARCHAR(255) NOT NULL,
  operation VARCHAR(128) NOT NULL,
  service_request_id VARCHAR(64) NOT NULL,
  target_runner_log_path VARCHAR(1024) NOT NULL,
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
  KEY idx_plugin_service_call_caller (caller_plugin_id, id),
  KEY idx_plugin_service_call_target (target_plugin_id, id),
  KEY idx_plugin_service_call_operation (operation, id),
  CONSTRAINT fk_plugin_service_call_caller FOREIGN KEY (caller_plugin_id) REFERENCES plugin_runtime (plugin_id),
  CONSTRAINT fk_plugin_service_call_target FOREIGN KEY (target_plugin_id) REFERENCES plugin_runtime (plugin_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS job (
  id BIGINT NOT NULL AUTO_INCREMENT,
  job_name VARCHAR(255) NOT NULL,
  plugin_id VARCHAR(128) NOT NULL,
  cron_expression VARCHAR(128) NOT NULL,
  job_status VARCHAR(32) NOT NULL DEFAULT 'ENABLED',
  misfire_policy VARCHAR(32) NOT NULL DEFAULT 'DEFAULT',
  concurrent BOOLEAN NOT NULL DEFAULT TRUE,
  input_json JSON NULL,
  timeout_millis BIGINT NOT NULL DEFAULT 120000,
  executed_status VARCHAR(32) NOT NULL DEFAULT 'IDLE',
  remark VARCHAR(500) DEFAULT NULL,
  created_by VARCHAR(128) NOT NULL DEFAULT '',
  updated_by VARCHAR(128) NOT NULL DEFAULT '',
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_job_plugin (plugin_id),
  KEY idx_job_status (job_status),
  KEY idx_job_executed_status (executed_status),
  CONSTRAINT fk_job_plugin_runtime FOREIGN KEY (plugin_id) REFERENCES plugin_runtime (plugin_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS job_execution (
  id BIGINT NOT NULL AUTO_INCREMENT,
  job_id BIGINT NOT NULL,
  plugin_id VARCHAR(128) NOT NULL,
  instance_id VARCHAR(128) NOT NULL DEFAULT '',
  request_id VARCHAR(128) NOT NULL DEFAULT '',
  status VARCHAR(32) NOT NULL,
  trigger_type VARCHAR(32) NOT NULL,
  job_name VARCHAR(255) NOT NULL DEFAULT '',
  started_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  finished_at TIMESTAMP(3) NULL,
  duration_millis BIGINT NULL,
  message VARCHAR(1000) DEFAULT NULL,
  result_json JSON NULL,
  error_type VARCHAR(512) DEFAULT NULL,
  error_message TEXT DEFAULT NULL,
  runner_log_path VARCHAR(1024) DEFAULT NULL,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_job_execution_job (job_id, started_at),
  KEY idx_job_execution_plugin (plugin_id, started_at),
  KEY idx_job_execution_status (status, started_at),
  KEY idx_job_execution_started_status (started_at, status),
  KEY idx_job_execution_request (plugin_id, request_id),
  KEY idx_job_execution_finished (finished_at),
  CONSTRAINT fk_job_execution_job FOREIGN KEY (job_id) REFERENCES job (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS plugin_config (
  id BIGINT NOT NULL AUTO_INCREMENT,
  plugin_id VARCHAR(128) NOT NULL,
  config_json JSON NOT NULL,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_plugin_config_plugin_id (plugin_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS plugin_account (
  id BIGINT NOT NULL AUTO_INCREMENT,
  plugin_id VARCHAR(128) NOT NULL,
  account_id VARCHAR(128) NOT NULL,
  display_name VARCHAR(255) NOT NULL DEFAULT '',
  account_json JSON NOT NULL,
  enabled BOOLEAN NOT NULL DEFAULT TRUE,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_plugin_account_plugin_account (plugin_id, account_id),
  KEY idx_plugin_account_plugin_enabled (plugin_id, enabled)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS plugin_data (
  id BIGINT NOT NULL AUTO_INCREMENT,
  plugin_id VARCHAR(128) NOT NULL,
  collection_name VARCHAR(128) NOT NULL,
  data_key VARCHAR(255) NOT NULL,
  data_json JSON NOT NULL,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_plugin_data_key (plugin_id, collection_name, data_key),
  KEY idx_plugin_data_collection (plugin_id, collection_name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS plugin_task_log (
  id BIGINT NOT NULL AUTO_INCREMENT,
  plugin_id VARCHAR(128) NOT NULL,
  instance_id VARCHAR(128) NOT NULL,
  job_id VARCHAR(128) NOT NULL DEFAULT '',
  request_id VARCHAR(128) NOT NULL DEFAULT '',
  level VARCHAR(16) NOT NULL,
  message TEXT NOT NULL,
  sequence_no BIGINT NOT NULL DEFAULT 0,
  event_time TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_plugin_task_log_request (plugin_id, request_id, sequence_no),
  KEY idx_plugin_task_log_job (plugin_id, job_id, event_time),
  KEY idx_plugin_task_log_event_time (event_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS plugin_audit_log (
  id BIGINT NOT NULL AUTO_INCREMENT,
  plugin_id VARCHAR(128) NOT NULL DEFAULT '',
  instance_id VARCHAR(128) NOT NULL DEFAULT '',
  actor VARCHAR(128) NOT NULL DEFAULT '',
  action VARCHAR(64) NOT NULL,
  detail_json JSON NOT NULL,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_plugin_audit_plugin (plugin_id, created_at),
  KEY idx_plugin_audit_action (action, created_at),
  KEY idx_plugin_audit_created (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
