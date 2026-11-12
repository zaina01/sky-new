-- Legacy pre-upgrade patch only. Do not run this after
-- upgrade-data-scope-plugin-installation-mysql.sql; the new
-- plugin_installation table already contains these columns.
USE sky_new;

ALTER TABLE plugin_runtime
  ADD COLUMN remote_debug_enabled BOOLEAN NOT NULL DEFAULT FALSE AFTER dependency_jars_json,
  ADD COLUMN remote_debug_port INT NOT NULL DEFAULT 5005 AFTER remote_debug_enabled,
  ADD COLUMN remote_debug_suspend BOOLEAN NOT NULL DEFAULT TRUE AFTER remote_debug_port;
