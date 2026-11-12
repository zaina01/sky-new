-- Idempotent upgrade for per-user plugin account list columns.
USE sky_new;

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
