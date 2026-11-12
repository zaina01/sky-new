-- One-time upgrade from the legacy plugin_runtime/plugin_id schema.
-- Target: MySQL 8.0. Run after a verified backup and while the host is stopped.
-- This script is intentionally not idempotent: a failed run must be restored
-- from backup before retrying.

USE sky_new;
SET @OLD_FOREIGN_KEY_CHECKS = @@FOREIGN_KEY_CHECKS;
SET FOREIGN_KEY_CHECKS = 0;

CREATE TABLE sky_department (
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

INSERT INTO sky_department
  (department_id, parent_id, department_name, sort_order, enabled, deleted)
VALUES (1, NULL, '默认部门', 0, TRUE, FALSE);

ALTER TABLE sky_user
  ADD COLUMN department_id BIGINT NULL AFTER avatar;
UPDATE sky_user SET department_id=1 WHERE department_id IS NULL;
ALTER TABLE sky_user
  MODIFY department_id BIGINT NOT NULL DEFAULT 1,
  ADD KEY idx_sky_user_department (department_id, enabled),
  ADD CONSTRAINT fk_sky_user_department FOREIGN KEY (department_id) REFERENCES sky_department (department_id);

ALTER TABLE sky_role
  ADD COLUMN data_scope VARCHAR(32) NOT NULL DEFAULT 'SELF' AFTER description,
  ADD KEY idx_sky_role_data_scope (data_scope);
UPDATE sky_role SET data_scope='ALL';

CREATE TABLE sky_role_department (
  role_id BIGINT NOT NULL,
  department_id BIGINT NOT NULL,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (role_id, department_id),
  CONSTRAINT fk_sky_role_department_role FOREIGN KEY (role_id) REFERENCES sky_role (role_id),
  CONSTRAINT fk_sky_role_department_department FOREIGN KEY (department_id) REFERENCES sky_department (department_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

INSERT IGNORE INTO sky_permission (permission_name, description) VALUES
  ('plugin:notification-channel:bind', 'Bind channels to plugin installations'),
  ('job:notification:bind', 'Bind channels to jobs'),
  ('system:department:read', 'Read departments'),
  ('system:department:write', 'Create and update departments'),
  ('system:department:delete', 'Delete departments'),
  ('system:data-ownership:transfer', 'Transfer owned resources');

INSERT IGNORE INTO sky_role_permission (role_id, permission_id)
SELECT r.role_id, p.permission_id
FROM sky_role r
CROSS JOIN sky_permission p
WHERE r.role_name='ADMIN';

SET @migration_owner_user_id = COALESCE(
  (SELECT u.user_id FROM sky_user u
   JOIN sky_user_role ur ON ur.user_id=u.user_id
   JOIN sky_role r ON r.role_id=ur.role_id AND r.role_name='ADMIN'
   ORDER BY u.created_at, u.user_id LIMIT 1),
  (SELECT user_id FROM sky_user ORDER BY created_at, user_id LIMIT 1)
);

CREATE TABLE plugin_definition (
  plugin_id VARCHAR(128) NOT NULL,
  version VARCHAR(64) NOT NULL DEFAULT '',
  display_name VARCHAR(255) NOT NULL DEFAULT '',
  main_class VARCHAR(512) NOT NULL DEFAULT '',
  artifact_path VARCHAR(1024) NOT NULL DEFAULT '',
  artifact_sha256 CHAR(64) NOT NULL DEFAULT '',
  capabilities_json JSON NOT NULL,
  config_schema_json JSON NOT NULL,
  account_schema_json JSON NOT NULL,
  dependency_jars_json JSON NOT NULL,
  uploaded_by VARCHAR(128) NOT NULL DEFAULT '',
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (plugin_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

INSERT INTO plugin_definition (
  plugin_id, version, display_name, main_class, artifact_path, artifact_sha256,
  capabilities_json, config_schema_json, account_schema_json, dependency_jars_json,
  uploaded_by, created_at, updated_at
)
SELECT plugin_id, version, display_name, main_class, artifact_path, artifact_sha256,
       COALESCE(capabilities_json, JSON_OBJECT()),
       COALESCE(config_schema_json, JSON_OBJECT()),
       COALESCE(account_schema_json, JSON_OBJECT()),
       COALESCE(dependency_jars_json, JSON_ARRAY()),
       '', created_at, updated_at
FROM plugin_runtime;

-- Keep the migration map as a regular staging table so an interrupted
-- migration can be resumed after the client reconnects. It is dropped after
-- all dependent data has been migrated.
CREATE TABLE migration_plugin_installation_map (
  plugin_id VARCHAR(128) NOT NULL PRIMARY KEY,
  installation_id VARCHAR(64) NOT NULL UNIQUE
);
INSERT INTO migration_plugin_installation_map (plugin_id, installation_id)
SELECT plugin_id, UUID() FROM plugin_runtime;

CREATE TABLE plugin_installation (
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
  KEY idx_plugin_installation_state (state, deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

INSERT INTO plugin_installation (
  installation_id, plugin_id, instance_id, installation_name, runtime_type, state,
  runner_host, runner_port, work_dir, remote_debug_enabled, remote_debug_port,
  remote_debug_suspend, owner_user_id, owner_dept_id, started_at, created_at, updated_at
)
SELECT m.installation_id, r.plugin_id, r.instance_id,
       COALESCE(NULLIF(r.display_name, ''), r.plugin_id), r.runtime_type, r.state,
       r.runner_host, r.runner_port, NULLIF(r.work_dir, ''), r.remote_debug_enabled,
       r.remote_debug_port, r.remote_debug_suspend, @migration_owner_user_id, 1,
       r.started_at, r.created_at, r.updated_at
FROM plugin_runtime r
JOIN migration_plugin_installation_map m ON m.plugin_id=r.plugin_id;

ALTER TABLE login_log ADD COLUMN owner_dept_id BIGINT NULL AFTER user_id;
UPDATE login_log l JOIN sky_user u ON u.user_id=l.user_id
SET l.owner_dept_id=u.department_id;
ALTER TABLE login_log ADD KEY idx_login_log_owner (owner_dept_id, user_id, login_time);

ALTER TABLE system_log ADD COLUMN owner_dept_id BIGINT NULL AFTER operator_user_id;
UPDATE system_log l JOIN sky_user u ON u.user_id=l.operator_user_id
SET l.owner_dept_id=u.department_id;
ALTER TABLE system_log ADD KEY idx_system_log_owner (owner_dept_id, operator_user_id, create_time);

ALTER TABLE notification_channel
  ADD COLUMN owner_user_id VARCHAR(64) NULL AFTER remark,
  ADD COLUMN owner_dept_id BIGINT NULL AFTER owner_user_id;
UPDATE notification_channel c
LEFT JOIN sky_user u ON u.username=c.created_by
SET c.owner_user_id=COALESCE(u.user_id, @migration_owner_user_id),
    c.owner_dept_id=COALESCE(u.department_id, 1);
ALTER TABLE notification_channel
  MODIFY owner_user_id VARCHAR(64) NOT NULL,
  MODIFY owner_dept_id BIGINT NOT NULL,
  ADD KEY idx_notification_channel_owner (owner_dept_id, owner_user_id);

CREATE TABLE plugin_installation_channel (
  installation_id VARCHAR(64) NOT NULL,
  channel_id VARCHAR(128) NOT NULL,
  created_by VARCHAR(128) NOT NULL DEFAULT 'migration',
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (installation_id, channel_id),
  KEY idx_plugin_installation_channel_channel (channel_id, installation_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

ALTER TABLE job DROP FOREIGN KEY fk_job_plugin_runtime;
ALTER TABLE job
  ADD COLUMN plugin_installation_id VARCHAR(64) NULL AFTER job_name,
  ADD COLUMN owner_user_id VARCHAR(64) NULL AFTER remark,
  ADD COLUMN owner_dept_id BIGINT NULL AFTER owner_user_id;
UPDATE job j
JOIN migration_plugin_installation_map m ON m.plugin_id=j.plugin_id
LEFT JOIN sky_user u ON u.username=j.created_by
SET j.plugin_installation_id=m.installation_id,
    j.owner_user_id=COALESCE(u.user_id, @migration_owner_user_id),
    j.owner_dept_id=COALESCE(u.department_id, 1);
ALTER TABLE job
  DROP INDEX idx_job_plugin,
  DROP COLUMN plugin_id,
  MODIFY plugin_installation_id VARCHAR(64) NOT NULL,
  MODIFY owner_user_id VARCHAR(64) NOT NULL,
  MODIFY owner_dept_id BIGINT NOT NULL,
  ADD KEY idx_job_installation (plugin_installation_id),
  ADD KEY idx_job_owner (owner_dept_id, owner_user_id);

CREATE TABLE job_notification_channel (
  job_id BIGINT NOT NULL,
  channel_id VARCHAR(128) NOT NULL,
  created_by VARCHAR(128) NOT NULL DEFAULT 'migration',
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (job_id, channel_id),
  KEY idx_job_notification_channel_channel (channel_id, job_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

ALTER TABLE job_execution
  DROP FOREIGN KEY fk_job_execution_job,
  ADD COLUMN plugin_installation_id VARCHAR(64) NULL AFTER job_id,
  ADD COLUMN owner_user_id VARCHAR(64) NULL AFTER runner_log_path,
  ADD COLUMN owner_dept_id BIGINT NULL AFTER owner_user_id;
UPDATE job_execution e JOIN job j ON j.id=e.job_id
SET e.plugin_installation_id=j.plugin_installation_id,
    e.owner_user_id=j.owner_user_id,
    e.owner_dept_id=j.owner_dept_id;
ALTER TABLE job_execution
  MODIFY plugin_installation_id VARCHAR(64) NOT NULL,
  MODIFY instance_id VARCHAR(255) NOT NULL DEFAULT '',
  DROP INDEX idx_job_execution_request,
  ADD KEY idx_job_execution_installation (plugin_installation_id, started_at),
  ADD KEY idx_job_execution_owner (owner_dept_id, owner_user_id, started_at),
  ADD KEY idx_job_execution_request (plugin_installation_id, request_id),
  ADD CONSTRAINT fk_job_execution_job FOREIGN KEY (job_id) REFERENCES job (id) ON DELETE CASCADE;

CREATE TABLE notification_rule_channel (
  rule_id VARCHAR(128) NOT NULL,
  channel_id VARCHAR(128) NOT NULL,
  created_by VARCHAR(128) NOT NULL DEFAULT 'migration',
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (rule_id, channel_id),
  KEY idx_notification_rule_channel_channel (channel_id, rule_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

INSERT IGNORE INTO notification_rule_channel (rule_id, channel_id, created_by)
SELECT r.rule_id, channels.channel_id, 'migration'
FROM notification_rule r
JOIN JSON_TABLE(
  COALESCE(r.channel_ids_json, JSON_ARRAY()),
  '$[*]' COLUMNS(channel_id VARCHAR(128) PATH '$')
) channels
JOIN notification_channel c ON c.channel_id=channels.channel_id;

ALTER TABLE notification_rule
  DROP INDEX idx_notification_rule_match,
  ADD COLUMN plugin_installation_id VARCHAR(64) NULL AFTER event_type,
  ADD COLUMN owner_user_id VARCHAR(64) NULL AFTER remark,
  ADD COLUMN owner_dept_id BIGINT NULL AFTER owner_user_id,
  MODIFY job_id VARCHAR(64) NULL;
UPDATE notification_rule r
LEFT JOIN migration_plugin_installation_map m ON m.plugin_id=r.plugin_id
LEFT JOIN sky_user u ON u.username=r.created_by
SET r.plugin_installation_id=m.installation_id,
    r.owner_user_id=COALESCE(u.user_id, @migration_owner_user_id),
    r.owner_dept_id=COALESCE(u.department_id, 1),
    r.job_id=NULLIF(r.job_id, '');
ALTER TABLE notification_rule
  DROP COLUMN plugin_id,
  DROP COLUMN channel_ids_json,
  MODIFY job_id BIGINT NULL,
  MODIFY owner_user_id VARCHAR(64) NOT NULL,
  MODIFY owner_dept_id BIGINT NOT NULL,
  ADD KEY idx_notification_rule_match (enabled, event_type, plugin_installation_id, job_id, severity),
  ADD KEY idx_notification_rule_owner (owner_dept_id, owner_user_id);

ALTER TABLE notification_send_history
  ADD COLUMN installation_id VARCHAR(64) NOT NULL DEFAULT '' AFTER source_type,
  ADD COLUMN source_owner_user_id VARCHAR(64) NULL AFTER execution_id,
  ADD COLUMN source_owner_dept_id BIGINT NULL AFTER source_owner_user_id,
  ADD COLUMN channel_owner_user_id VARCHAR(64) NULL AFTER source_owner_dept_id,
  ADD COLUMN channel_owner_dept_id BIGINT NULL AFTER channel_owner_user_id;
-- MySQL does not guarantee short-circuit evaluation for predicates joined by
-- AND. Put the numeric guard inside CASE so blank/non-numeric legacy job ids
-- never reach CAST under strict SQL mode.
UPDATE notification_send_history h
LEFT JOIN migration_plugin_installation_map m ON m.plugin_id=h.plugin_id
LEFT JOIN job j ON j.id=CAST(
  CASE WHEN h.job_id REGEXP '^[0-9]+$' THEN h.job_id ELSE NULL END
  AS UNSIGNED
)
LEFT JOIN plugin_installation i ON i.installation_id=m.installation_id
LEFT JOIN notification_channel c ON c.channel_id=h.channel_id
SET h.installation_id=COALESCE(m.installation_id, ''),
    h.source_owner_user_id=COALESCE(j.owner_user_id, i.owner_user_id),
    h.source_owner_dept_id=COALESCE(j.owner_dept_id, i.owner_dept_id),
    h.channel_owner_user_id=c.owner_user_id,
    h.channel_owner_dept_id=c.owner_dept_id;
ALTER TABLE notification_send_history
  MODIFY instance_id VARCHAR(255) NOT NULL DEFAULT '',
  ADD KEY idx_notification_history_installation (installation_id, created_at),
  ADD KEY idx_notification_history_source_owner (source_owner_dept_id, source_owner_user_id, created_at),
  ADD KEY idx_notification_history_channel_owner (channel_owner_dept_id, channel_owner_user_id, created_at);

-- Plugin sends with a job establish both whitelist levels. Service-style
-- sends establish only the installation pool. Rule channels are deliberately
-- excluded from these statements.
INSERT IGNORE INTO plugin_installation_channel (installation_id, channel_id, created_by)
SELECT DISTINCT h.installation_id, h.channel_id, 'history-migration'
FROM notification_send_history h
JOIN notification_channel c ON c.channel_id=h.channel_id
WHERE h.source_type='PLUGIN_RUNTIME' AND h.installation_id<>'';

INSERT IGNORE INTO job_notification_channel (job_id, channel_id, created_by)
SELECT DISTINCT j.id, h.channel_id, 'history-migration'
FROM notification_send_history h
JOIN job j ON j.id=CAST(
  CASE WHEN h.job_id REGEXP '^[0-9]+$' THEN h.job_id ELSE NULL END
  AS UNSIGNED
)
          AND j.plugin_installation_id=h.installation_id
JOIN plugin_installation_channel pic
  ON pic.installation_id=j.plugin_installation_id AND pic.channel_id=h.channel_id
WHERE h.source_type='PLUGIN_RUNTIME';

ALTER TABLE plugin_config
  ADD COLUMN installation_id VARCHAR(64) NULL AFTER id;
UPDATE plugin_config c JOIN migration_plugin_installation_map m ON m.plugin_id=c.plugin_id
SET c.installation_id=m.installation_id;
ALTER TABLE plugin_config
  DROP INDEX uk_plugin_config_plugin_id,
  DROP COLUMN plugin_id,
  MODIFY installation_id VARCHAR(64) NOT NULL,
  ADD UNIQUE KEY uk_plugin_config_installation (installation_id);

ALTER TABLE plugin_account
  ADD COLUMN installation_id VARCHAR(64) NULL AFTER id;
UPDATE plugin_account a JOIN migration_plugin_installation_map m ON m.plugin_id=a.plugin_id
SET a.installation_id=m.installation_id;
ALTER TABLE plugin_account
  DROP INDEX uk_plugin_account_plugin_account,
  DROP INDEX idx_plugin_account_plugin_enabled,
  DROP COLUMN plugin_id,
  MODIFY installation_id VARCHAR(64) NOT NULL,
  ADD UNIQUE KEY uk_plugin_account_installation_account (installation_id, account_id),
  ADD KEY idx_plugin_account_enabled (installation_id, enabled);

ALTER TABLE plugin_data
  ADD COLUMN installation_id VARCHAR(64) NULL AFTER id;
UPDATE plugin_data d JOIN migration_plugin_installation_map m ON m.plugin_id=d.plugin_id
SET d.installation_id=m.installation_id;
ALTER TABLE plugin_data
  DROP INDEX uk_plugin_data_key,
  DROP INDEX idx_plugin_data_collection,
  DROP COLUMN plugin_id,
  MODIFY installation_id VARCHAR(64) NOT NULL,
  ADD UNIQUE KEY uk_plugin_data_key (installation_id, collection_name, data_key),
  ADD KEY idx_plugin_data_collection (installation_id, collection_name);

ALTER TABLE plugin_task_log
  ADD COLUMN installation_id VARCHAR(64) NULL AFTER id;
UPDATE plugin_task_log l JOIN migration_plugin_installation_map m ON m.plugin_id=l.plugin_id
SET l.installation_id=m.installation_id;
ALTER TABLE plugin_task_log
  DROP INDEX idx_plugin_task_log_request,
  DROP INDEX idx_plugin_task_log_job,
  MODIFY installation_id VARCHAR(64) NOT NULL,
  MODIFY instance_id VARCHAR(255) NOT NULL,
  ADD KEY idx_plugin_task_log_request (installation_id, request_id, sequence_no),
  ADD KEY idx_plugin_task_log_job (installation_id, job_id, event_time);

ALTER TABLE plugin_audit_log
  ADD COLUMN installation_id VARCHAR(64) NOT NULL DEFAULT '' AFTER id,
  ADD COLUMN owner_user_id VARCHAR(64) NULL AFTER instance_id,
  ADD COLUMN owner_dept_id BIGINT NULL AFTER owner_user_id,
  MODIFY instance_id VARCHAR(255) NOT NULL DEFAULT '';
UPDATE plugin_audit_log l
JOIN migration_plugin_installation_map m ON m.plugin_id=l.plugin_id
JOIN plugin_installation i ON i.installation_id=m.installation_id
SET l.installation_id=m.installation_id,
    l.owner_user_id=i.owner_user_id,
    l.owner_dept_id=i.owner_dept_id;
ALTER TABLE plugin_audit_log
  ADD KEY idx_plugin_audit_installation (installation_id, created_at),
  ADD KEY idx_plugin_audit_owner (owner_dept_id, owner_user_id, created_at);

ALTER TABLE plugin_service_permission
  DROP FOREIGN KEY fk_plugin_service_permission_caller,
  DROP FOREIGN KEY fk_plugin_service_permission_target,
  DROP INDEX uk_plugin_service_permission,
  DROP INDEX idx_plugin_service_permission_target,
  ADD COLUMN caller_installation_id VARCHAR(64) NULL AFTER id,
  ADD COLUMN target_installation_id VARCHAR(64) NULL AFTER caller_installation_id;
UPDATE plugin_service_permission p
JOIN migration_plugin_installation_map caller ON caller.plugin_id=p.caller_plugin_id
JOIN migration_plugin_installation_map target ON target.plugin_id=p.target_plugin_id
SET p.caller_installation_id=caller.installation_id,
    p.target_installation_id=target.installation_id;
ALTER TABLE plugin_service_permission
  DROP COLUMN caller_plugin_id,
  DROP COLUMN target_plugin_id,
  MODIFY caller_installation_id VARCHAR(64) NOT NULL,
  MODIFY target_installation_id VARCHAR(64) NOT NULL,
  ADD UNIQUE KEY uk_plugin_service_permission (caller_installation_id, target_installation_id, operation),
  ADD KEY idx_plugin_service_permission_target (target_installation_id, operation, enabled);

ALTER TABLE plugin_service_call_log
  DROP FOREIGN KEY fk_plugin_service_call_caller,
  DROP FOREIGN KEY fk_plugin_service_call_target,
  ADD COLUMN execution_id BIGINT NULL AFTER job_id,
  ADD COLUMN caller_installation_id VARCHAR(64) NULL AFTER execution_id,
  ADD COLUMN caller_owner_user_id VARCHAR(64) NULL AFTER caller_plugin_id,
  ADD COLUMN caller_owner_dept_id BIGINT NULL AFTER caller_owner_user_id,
  ADD COLUMN target_installation_id VARCHAR(64) NULL AFTER caller_request_id,
  ADD COLUMN target_owner_user_id VARCHAR(64) NULL AFTER target_plugin_id,
  ADD COLUMN target_owner_dept_id BIGINT NULL AFTER target_owner_user_id,
  MODIFY caller_instance_id VARCHAR(255) NOT NULL,
  MODIFY target_instance_id VARCHAR(255) NOT NULL DEFAULT '',
  MODIFY target_plugin_id VARCHAR(128) NULL;
UPDATE plugin_service_call_log l
LEFT JOIN migration_plugin_installation_map cm ON cm.plugin_id=l.caller_plugin_id
LEFT JOIN migration_plugin_installation_map tm ON tm.plugin_id=l.target_plugin_id
LEFT JOIN plugin_installation ci ON ci.installation_id=cm.installation_id
LEFT JOIN plugin_installation ti ON ti.installation_id=tm.installation_id
SET l.caller_installation_id=cm.installation_id,
    l.caller_owner_user_id=ci.owner_user_id,
    l.caller_owner_dept_id=ci.owner_dept_id,
    l.target_installation_id=tm.installation_id,
    l.target_owner_user_id=ti.owner_user_id,
    l.target_owner_dept_id=ti.owner_dept_id;
ALTER TABLE plugin_service_call_log
  MODIFY caller_installation_id VARCHAR(64) NOT NULL,
  ADD KEY idx_plugin_service_call_caller_installation (caller_installation_id, id),
  ADD KEY idx_plugin_service_call_target_installation (target_installation_id, id),
  ADD KEY idx_plugin_service_call_caller_owner (caller_owner_dept_id, caller_owner_user_id, id),
  ADD KEY idx_plugin_service_call_target_owner (target_owner_dept_id, target_owner_user_id, id);

ALTER TABLE plugin_installation
  ADD CONSTRAINT fk_plugin_installation_definition FOREIGN KEY (plugin_id) REFERENCES plugin_definition (plugin_id),
  ADD CONSTRAINT fk_plugin_installation_owner_user FOREIGN KEY (owner_user_id) REFERENCES sky_user (user_id),
  ADD CONSTRAINT fk_plugin_installation_owner_dept FOREIGN KEY (owner_dept_id) REFERENCES sky_department (department_id);
ALTER TABLE notification_channel
  ADD CONSTRAINT fk_notification_channel_owner_user FOREIGN KEY (owner_user_id) REFERENCES sky_user (user_id),
  ADD CONSTRAINT fk_notification_channel_owner_dept FOREIGN KEY (owner_dept_id) REFERENCES sky_department (department_id);
ALTER TABLE plugin_installation_channel
  ADD CONSTRAINT fk_plugin_installation_channel_installation FOREIGN KEY (installation_id) REFERENCES plugin_installation (installation_id),
  ADD CONSTRAINT fk_plugin_installation_channel_channel FOREIGN KEY (channel_id) REFERENCES notification_channel (channel_id);
ALTER TABLE job
  ADD CONSTRAINT fk_job_installation FOREIGN KEY (plugin_installation_id) REFERENCES plugin_installation (installation_id),
  ADD CONSTRAINT fk_job_owner_user FOREIGN KEY (owner_user_id) REFERENCES sky_user (user_id),
  ADD CONSTRAINT fk_job_owner_dept FOREIGN KEY (owner_dept_id) REFERENCES sky_department (department_id);
ALTER TABLE job_notification_channel
  ADD CONSTRAINT fk_job_notification_channel_job FOREIGN KEY (job_id) REFERENCES job (id) ON DELETE CASCADE,
  ADD CONSTRAINT fk_job_notification_channel_channel FOREIGN KEY (channel_id) REFERENCES notification_channel (channel_id);
ALTER TABLE notification_rule
  ADD CONSTRAINT fk_notification_rule_installation FOREIGN KEY (plugin_installation_id) REFERENCES plugin_installation (installation_id),
  ADD CONSTRAINT fk_notification_rule_job FOREIGN KEY (job_id) REFERENCES job (id) ON DELETE SET NULL,
  ADD CONSTRAINT fk_notification_rule_owner_user FOREIGN KEY (owner_user_id) REFERENCES sky_user (user_id),
  ADD CONSTRAINT fk_notification_rule_owner_dept FOREIGN KEY (owner_dept_id) REFERENCES sky_department (department_id);
ALTER TABLE notification_rule_channel
  ADD CONSTRAINT fk_notification_rule_channel_rule FOREIGN KEY (rule_id) REFERENCES notification_rule (rule_id) ON DELETE CASCADE,
  ADD CONSTRAINT fk_notification_rule_channel_channel FOREIGN KEY (channel_id) REFERENCES notification_channel (channel_id);
ALTER TABLE plugin_service_permission
  ADD CONSTRAINT fk_plugin_service_permission_caller FOREIGN KEY (caller_installation_id) REFERENCES plugin_installation (installation_id),
  ADD CONSTRAINT fk_plugin_service_permission_target FOREIGN KEY (target_installation_id) REFERENCES plugin_installation (installation_id);
ALTER TABLE plugin_config
  ADD CONSTRAINT fk_plugin_config_installation FOREIGN KEY (installation_id) REFERENCES plugin_installation (installation_id);
ALTER TABLE plugin_account
  ADD CONSTRAINT fk_plugin_account_installation FOREIGN KEY (installation_id) REFERENCES plugin_installation (installation_id);
ALTER TABLE plugin_data
  ADD CONSTRAINT fk_plugin_data_installation FOREIGN KEY (installation_id) REFERENCES plugin_installation (installation_id);

DROP TABLE plugin_runtime;
DROP TABLE migration_plugin_installation_map;

-- A migration may be resumed in a new client session, in which case the
-- session variable captured at the beginning no longer exists. Default to
-- enabling foreign-key checks rather than assigning NULL.
SET FOREIGN_KEY_CHECKS = COALESCE(@OLD_FOREIGN_KEY_CHECKS, 1);
