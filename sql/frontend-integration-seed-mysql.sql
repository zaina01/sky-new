-- Idempotent compatibility fixtures for manual frontend/backend integration.
-- Run only after sql/schema-mysql.sql has been applied to sky_new_frontend_it.
USE sky_new_frontend_it;

INSERT INTO sky_department (
  department_id, parent_id, department_name, sort_order, enabled, deleted
) VALUES
  (910001, 1, 'Frontend IT Seed Enabled', 910001, TRUE, FALSE),
  (910002, 1, 'Frontend IT Seed Legacy Disabled', 910002, FALSE, FALSE)
ON DUPLICATE KEY UPDATE
  parent_id=VALUES(parent_id),
  department_name=VALUES(department_name),
  sort_order=VALUES(sort_order),
  enabled=VALUES(enabled),
  deleted=FALSE;

INSERT INTO sky_user (
  user_id, username, password_hash, email, department_id, enabled
) VALUES (
  'frontend-it-legacy-user',
  'frontend_it_seed_legacy',
  '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy',
  'frontend-it-seed-legacy@example.test',
  910002,
  TRUE
)
ON DUPLICATE KEY UPDATE
  email=VALUES(email),
  department_id=VALUES(department_id),
  enabled=TRUE;

INSERT INTO sky_role (role_name, description, data_scope)
VALUES ('FRONTEND_IT_SEED_CUSTOM', 'Frontend integration seed CUSTOM role', 'CUSTOM')
ON DUPLICATE KEY UPDATE
  description=VALUES(description),
  data_scope='CUSTOM';

SET @frontend_it_custom_role_id = (
  SELECT role_id FROM sky_role WHERE role_name='FRONTEND_IT_SEED_CUSTOM' LIMIT 1
);

DELETE FROM sky_role_department
WHERE role_id=@frontend_it_custom_role_id;

INSERT INTO sky_role_department (role_id, department_id)
VALUES (@frontend_it_custom_role_id, 910002);

INSERT IGNORE INTO sky_user_role (user_id, role_id)
VALUES ('frontend-it-legacy-user', @frontend_it_custom_role_id);

SELECT department_id, department_name, enabled
FROM sky_department
WHERE department_id IN (910001, 910002)
ORDER BY department_id;

SELECT role_name, data_scope, department_id
FROM sky_role r
JOIN sky_role_department rd ON rd.role_id=r.role_id
WHERE r.role_name='FRONTEND_IT_SEED_CUSTOM';
