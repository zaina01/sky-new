-- Run once before starting the upgraded host. Back up the database first.
ALTER TABLE plugin_definition
  ADD COLUMN runtime_engine VARCHAR(16) NOT NULL DEFAULT 'java',
  ADD COLUMN entry_point VARCHAR(512) NOT NULL DEFAULT '';
UPDATE plugin_definition SET entry_point=main_class WHERE runtime_engine='java';
ALTER TABLE job_execution
  ADD COLUMN runtime_engine VARCHAR(16) NOT NULL DEFAULT 'java',
  ADD COLUMN artifact_sha256 VARCHAR(64) NOT NULL DEFAULT '',
  ADD COLUMN environment_revision VARCHAR(64) NOT NULL DEFAULT '',
  ADD COLUMN interpreter_version VARCHAR(128) NOT NULL DEFAULT '';
CREATE TABLE IF NOT EXISTS script_environment_document (
  document_key VARCHAR(192) NOT NULL PRIMARY KEY,
  document_json JSON NOT NULL,
  updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3)
);
