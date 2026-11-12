USE sky_new;

-- Run after the initial administrator has registered.
SET @demo_owner_user_id = (
  SELECT u.user_id
  FROM sky_user u
  JOIN sky_user_role ur ON ur.user_id=u.user_id
  JOIN sky_role r ON r.role_id=ur.role_id AND r.role_name='ADMIN'
  ORDER BY u.created_at, u.user_id
  LIMIT 1
);
SET @demo_owner_dept_id = COALESCE(
  (SELECT department_id FROM sky_user WHERE user_id=@demo_owner_user_id),
  1
);

INSERT INTO plugin_definition (
  plugin_id, version, display_name, main_class, artifact_path, artifact_sha256,
  capabilities_json, config_schema_json, account_schema_json,
  dependency_jars_json, uploaded_by
) VALUES (
  'demo', '1.0.0', 'Demo Plugin', 'com.open.demo.TaskServiceImpl', '', '',
  JSON_OBJECT('task', true, 'data', true, 'config', true, 'account', true, 'notification', false, 'service', false),
  JSON_OBJECT('fields', JSON_ARRAY(JSON_OBJECT('name', 'message', 'label', 'Message', 'type', 'TEXT', 'required', false, 'secret', false))),
  JSON_OBJECT('fields', JSON_ARRAY()), JSON_ARRAY(), 'demo-seed'
) ON DUPLICATE KEY UPDATE
  version=VALUES(version), display_name=VALUES(display_name), main_class=VALUES(main_class),
  capabilities_json=VALUES(capabilities_json), config_schema_json=VALUES(config_schema_json),
  account_schema_json=VALUES(account_schema_json), updated_at=CURRENT_TIMESTAMP;

INSERT INTO plugin_installation (
  installation_id, plugin_id, instance_id, installation_name,
  runtime_type, state, owner_user_id, owner_dept_id
) VALUES (
  '00000000-0000-0000-0000-000000000001', 'demo', 'demo-dev-instance', 'Demo Default',
  'process', 'STOPPED', @demo_owner_user_id, @demo_owner_dept_id
) ON DUPLICATE KEY UPDATE
  installation_name=VALUES(installation_name), owner_user_id=VALUES(owner_user_id),
  owner_dept_id=VALUES(owner_dept_id), updated_at=CURRENT_TIMESTAMP;

INSERT INTO plugin_config (installation_id, config_json)
VALUES ('00000000-0000-0000-0000-000000000001', JSON_OBJECT('message', 'hello from mysql'))
ON DUPLICATE KEY UPDATE config_json=VALUES(config_json), updated_at=CURRENT_TIMESTAMP;

INSERT INTO plugin_account (installation_id, account_id, display_name, account_json, enabled)
VALUES ('00000000-0000-0000-0000-000000000001', 'default', 'Default Account', JSON_OBJECT('name', 'demo-account'), TRUE)
ON DUPLICATE KEY UPDATE display_name=VALUES(display_name), account_json=VALUES(account_json),
  enabled=VALUES(enabled), updated_at=CURRENT_TIMESTAMP;
