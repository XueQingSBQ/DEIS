CREATE TABLE IF NOT EXISTS regions (
    region_code VARCHAR(4) NOT NULL PRIMARY KEY,
    region_name VARCHAR(128) NOT NULL DEFAULT '',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS industries (
    industry VARCHAR(128) NOT NULL PRIMARY KEY,
    industry_code VARCHAR(2) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_industry_code (industry_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS identifier_metadata (
    identifier VARCHAR(30) NOT NULL PRIMARY KEY,
    region_code VARCHAR(4) NOT NULL,
    organization_code VARCHAR(4) NOT NULL,
    industry VARCHAR(128) NOT NULL,
    data_category VARCHAR(32) NOT NULL,
    registration_type VARCHAR(32) NOT NULL,
    date CHAR(8) NOT NULL,
    owner VARCHAR(100) NOT NULL,    description VARCHAR(500) DEFAULT '',
    application_scenario VARCHAR(200) DEFAULT '',
    name VARCHAR(200) DEFAULT '',
    entity_type VARCHAR(64) DEFAULT '',
    status VARCHAR(32) NOT NULL,
    create_time VARCHAR(64) NOT NULL,
    update_time VARCHAR(64) NOT NULL,
    additional_attributes TEXT,
    vector_dirty TINYINT(1) NOT NULL DEFAULT 1,
    revision BIGINT NOT NULL DEFAULT 0,
    KEY idx_metadata_owner (owner),    KEY idx_metadata_status (status),
    KEY idx_metadata_vector_dirty (vector_dirty),
    KEY idx_metadata_industry (industry),
    KEY idx_metadata_entity_type (entity_type),
    KEY idx_metadata_data_category (data_category),
    KEY idx_metadata_rt_date (registration_type, date),
    CONSTRAINT fk_metadata_region FOREIGN KEY (region_code) REFERENCES regions(region_code),
    CONSTRAINT fk_metadata_industry FOREIGN KEY (industry) REFERENCES industries(industry)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS resource (
    identifier VARCHAR(30) NOT NULL PRIMARY KEY,
    component_type VARCHAR(32) NOT NULL,
    storage_type VARCHAR(64) NOT NULL,
    access_method VARCHAR(64) NOT NULL,
    content_type VARCHAR(128) NOT NULL,
    format VARCHAR(128) NOT NULL,
    locator TEXT NOT NULL,
    size_bytes BIGINT NOT NULL DEFAULT 0,
    hash_value VARCHAR(255) DEFAULT '',
    etag VARCHAR(255) DEFAULT '',
    version_id VARCHAR(255) DEFAULT '',
    created_at VARCHAR(64) NOT NULL,
    extra_json TEXT,
    CONSTRAINT fk_resource_identifier FOREIGN KEY (identifier) REFERENCES identifier_metadata(identifier) ON DELETE CASCADE,
    KEY idx_resource_component_type (component_type),
    KEY idx_resource_storage_type (storage_type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS resource_version (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    identifier VARCHAR(30) NOT NULL,
    version_no BIGINT NOT NULL,
    component_type VARCHAR(32) NOT NULL,
    storage_type VARCHAR(64) NOT NULL,
    access_method VARCHAR(64) NOT NULL,
    content_type VARCHAR(128) NOT NULL,
    format VARCHAR(128) NOT NULL,
    locator TEXT NOT NULL,
    size_bytes BIGINT NOT NULL DEFAULT 0,
    hash_value VARCHAR(255) DEFAULT '',
    etag VARCHAR(255) DEFAULT '',
    provider_version_id VARCHAR(255) DEFAULT '',
    source_upload_id VARCHAR(64) DEFAULT '',
    bind_mode VARCHAR(32) NOT NULL DEFAULT 'direct',
    verify_status VARCHAR(32) NOT NULL DEFAULT '',
    created_at VARCHAR(64) NOT NULL,
    extra_json TEXT,
    UNIQUE KEY uk_resource_version_identifier_no (identifier, version_no),
    KEY idx_resource_version_identifier_time (identifier, created_at),
    CONSTRAINT fk_resource_version_identifier FOREIGN KEY (identifier) REFERENCES identifier_metadata(identifier) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS upload_session (
    upload_id VARCHAR(64) NOT NULL PRIMARY KEY,
    identifier VARCHAR(30) NOT NULL,
    component_type VARCHAR(32) NOT NULL,
    bucket VARCHAR(128) NOT NULL,
    object_key VARCHAR(512) NOT NULL,
    upload_mode VARCHAR(32) NOT NULL DEFAULT 'single',
    expected_size BIGINT NOT NULL DEFAULT 0,
    expected_sha256 VARCHAR(128) DEFAULT '',
    status VARCHAR(32) NOT NULL,
    expires_at VARCHAR(64) NOT NULL,
    extra_json TEXT,
    KEY idx_upload_identifier (identifier),
    KEY idx_upload_status (status),
    KEY idx_upload_expires (expires_at),
    CONSTRAINT fk_upload_identifier FOREIGN KEY (identifier) REFERENCES identifier_metadata(identifier) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS identifier_vector_index (
    identifier VARCHAR(30) NOT NULL PRIMARY KEY,
    vector_dim INT NOT NULL,
    vector_norm DOUBLE NOT NULL DEFAULT 0,
    vector_data LONGTEXT NOT NULL,
    update_time VARCHAR(64) NOT NULL DEFAULT '',
    CONSTRAINT fk_vector_identifier FOREIGN KEY (identifier) REFERENCES identifier_metadata(identifier) ON DELETE CASCADE,
    KEY idx_vector_dim (vector_dim)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

INSERT IGNORE INTO regions (region_code) VALUES ('1100');
INSERT IGNORE INTO industries (industry, industry_code) VALUES
('energy', '01'),
('traffic', '02'),
('health', '03'),
('transport', '04'),
('climate', '05'),
('agriculture', '06'),
('education', '07');

-- Compatibility migration for legacy DRIS schemas:
-- older deployments may store these fields as DATE/DATETIME, while SDK persists ISO-8601 strings.
ALTER TABLE identifier_metadata
    MODIFY COLUMN owner VARCHAR(100) NOT NULL,
    MODIFY COLUMN description VARCHAR(500) DEFAULT '',
    MODIFY COLUMN application_scenario VARCHAR(200) DEFAULT '',
    MODIFY COLUMN create_time VARCHAR(64) NOT NULL DEFAULT '',
    MODIFY COLUMN update_time VARCHAR(64) NOT NULL DEFAULT '';

ALTER TABLE resource
    MODIFY COLUMN created_at VARCHAR(64) NOT NULL DEFAULT '';

ALTER TABLE upload_session
    MODIFY COLUMN expires_at VARCHAR(64) NOT NULL DEFAULT '';

-- Compatibility migration for older schemas that only allow resource/tool.
-- continue-on-error=true ensures no startup failure when constraint name is absent.
ALTER TABLE upload_session DROP CONSTRAINT chk_upload_component_type;
ALTER TABLE upload_session DROP CHECK chk_upload_component_type;
ALTER TABLE upload_session
    ADD CONSTRAINT chk_upload_component_type
        CHECK (component_type IN ('resource', 'tool', 'service'));

ALTER TABLE identifier_metadata
    ADD COLUMN revision BIGINT NOT NULL DEFAULT 0;

ALTER TABLE identifier_metadata
    ADD COLUMN creator_user_id VARCHAR(64) DEFAULT '';

ALTER TABLE identifier_metadata
    ADD KEY idx_metadata_creator_user_id (creator_user_id);







