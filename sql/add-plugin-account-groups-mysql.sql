-- One-time upgrade for MySQL 8.0 installations using installation_id.
-- Run while the host is stopped, before deploying the new host version.
-- Existing accounts remain ungrouped; account JSON and timestamps are preserved.
USE sky_new;

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

ALTER TABLE plugin_account
  ADD COLUMN group_id BIGINT NULL AFTER account_id,
  ADD KEY idx_plugin_account_created (installation_id, created_at, id),
  ADD KEY idx_plugin_account_group_created (installation_id, group_id, created_at, id),
  ADD CONSTRAINT fk_plugin_account_group FOREIGN KEY (installation_id, group_id)
    REFERENCES plugin_account_group (installation_id, group_id);
