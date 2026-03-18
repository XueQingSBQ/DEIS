UPDATE resource
SET storage_type = 'cos'
WHERE storage_type = 'external_uri' AND locator LIKE 'cos://%';

UPDATE resource
SET storage_type = 's3'
WHERE storage_type = 'external_uri' AND locator LIKE 's3://%';

UPDATE resource
SET storage_type = 'local'
WHERE storage_type = 'external_uri' AND locator LIKE 'local://%';

UPDATE resource_version
SET storage_type = 'cos'
WHERE storage_type = 'external_uri' AND locator LIKE 'cos://%';

UPDATE resource_version
SET storage_type = 's3'
WHERE storage_type = 'external_uri' AND locator LIKE 's3://%';

UPDATE resource_version
SET storage_type = 'local'
WHERE storage_type = 'external_uri' AND locator LIKE 'local://%';

SET @drop_resource_role = IF(
    EXISTS (
        SELECT 1
        FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE()
          AND TABLE_NAME = 'resource'
          AND COLUMN_NAME = 'role'
    ),
    'ALTER TABLE resource DROP COLUMN role',
    'SELECT 1'
);
PREPARE stmt FROM @drop_resource_role;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @drop_resource_version_role = IF(
    EXISTS (
        SELECT 1
        FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE()
          AND TABLE_NAME = 'resource_version'
          AND COLUMN_NAME = 'role'
    ),
    'ALTER TABLE resource_version DROP COLUMN role',
    'SELECT 1'
);
PREPARE stmt FROM @drop_resource_version_role;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
