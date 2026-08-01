USE sky_new;

INSERT INTO plugin_runtime (
  plugin_id,
  instance_id,
  version,
  display_name,
  main_class,
  runtime_type,
  state,
  capabilities_json,
  config_schema_json,
  account_schema_json
) VALUES (
  'demo',
  'demo-dev-instance',
  '1.0.0',
  'Demo Plugin',
  'com.open.demo.TaskServiceImpl',
  'process',
  'STOPPED',
  JSON_OBJECT('task', true, 'data', true, 'config', true, 'account', true),
  JSON_OBJECT('fields', JSON_ARRAY(JSON_OBJECT('name', 'message', 'label', 'Message', 'type', 'TEXT', 'required', false, 'secret', false))),
  JSON_OBJECT('fields', JSON_ARRAY())
) ON DUPLICATE KEY UPDATE
  version = VALUES(version),
  display_name = VALUES(display_name),
  main_class = VALUES(main_class),
  capabilities_json = VALUES(capabilities_json),
  config_schema_json = VALUES(config_schema_json),
  account_schema_json = VALUES(account_schema_json),
  updated_at = CURRENT_TIMESTAMP;

INSERT INTO plugin_config (plugin_id, config_json)
VALUES ('demo', JSON_OBJECT('message', 'hello from mysql'))
ON DUPLICATE KEY UPDATE
  config_json = VALUES(config_json),
  updated_at = CURRENT_TIMESTAMP;

INSERT INTO plugin_account (plugin_id, account_id, display_name, account_json, enabled)
VALUES ('demo', 'default', 'Default Account', JSON_OBJECT('name', 'demo-account'), TRUE)
ON DUPLICATE KEY UPDATE
  display_name = VALUES(display_name),
  account_json = VALUES(account_json),
  enabled = VALUES(enabled),
  updated_at = CURRENT_TIMESTAMP;
