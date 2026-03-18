-- Extension schema for DRIS enterprise backend
-- NOTE: This script does NOT modify core identifier tables.
-- Core tables (identifier_metadata/resource/upload_session) remain unchanged.

CREATE TABLE IF NOT EXISTS iam_user (
    user_id VARCHAR(64) NOT NULL PRIMARY KEY,
    username VARCHAR(64) NOT NULL,
    display_name VARCHAR(128) NOT NULL,
    org_code VARCHAR(16) NOT NULL,
    node_id VARCHAR(64) DEFAULT '',
    email VARCHAR(128) DEFAULT '',
    phone VARCHAR(64) DEFAULT '',
    password_hash VARCHAR(255) NOT NULL DEFAULT '',
    status VARCHAR(32) NOT NULL DEFAULT 'active',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_iam_user_username (username),
    KEY idx_iam_user_org_status (org_code, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS iam_user_role (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    user_id VARCHAR(64) NOT NULL,
    role_code VARCHAR(64) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_iam_user_role (user_id, role_code),
    CONSTRAINT fk_iam_user_role_user FOREIGN KEY (user_id) REFERENCES iam_user(user_id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS iam_api_key (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    key_id VARCHAR(64) NOT NULL,
    user_id VARCHAR(64) NOT NULL,
    key_hash VARCHAR(128) NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'active',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expires_at TIMESTAMP NULL,
    last_used_at TIMESTAMP NULL,
    UNIQUE KEY uk_iam_api_key_id (key_id),
    KEY idx_iam_api_key_user_status (user_id, status),
    CONSTRAINT fk_iam_api_key_user FOREIGN KEY (user_id) REFERENCES iam_user(user_id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS identifier_history (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    identifier VARCHAR(30) NOT NULL,
    operation VARCHAR(64) NOT NULL,
    actor_user_id VARCHAR(64) DEFAULT '',
    actor_role VARCHAR(64) DEFAULT '',
    success TINYINT(1) NOT NULL DEFAULT 1,
    message VARCHAR(512) DEFAULT '',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    KEY idx_identifier_history_identifier_time (identifier, created_at),
    KEY idx_identifier_history_operation (operation)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS api_audit_log (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    trace_id VARCHAR(64) NOT NULL,
    user_id VARCHAR(64) DEFAULT '',
    role_code VARCHAR(64) DEFAULT '',
    method VARCHAR(16) NOT NULL,
    path VARCHAR(255) NOT NULL,
    status INT NOT NULL,
    duration_ms BIGINT NOT NULL,
    success TINYINT(1) NOT NULL,
    error_message VARCHAR(512) DEFAULT '',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    KEY idx_api_audit_time (created_at),
    KEY idx_api_audit_path (path),
    KEY idx_api_audit_role (role_code),
    KEY idx_api_audit_trace (trace_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Optional owner mapping table to decouple owner display text from owner identity.
-- Keep identifier_metadata.owner as-is for backward compatibility.
CREATE TABLE IF NOT EXISTS identifier_owner_binding (
    identifier VARCHAR(30) NOT NULL PRIMARY KEY,
    owner_user_id VARCHAR(64) NOT NULL,
    owner_org_code VARCHAR(16) NOT NULL,
    owner_node_id VARCHAR(64) DEFAULT '',
    owner_display_name VARCHAR(128) DEFAULT '',
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_owner_binding_identifier FOREIGN KEY (identifier) REFERENCES identifier_metadata(identifier) ON DELETE CASCADE,
    CONSTRAINT fk_owner_binding_user FOREIGN KEY (owner_user_id) REFERENCES iam_user(user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS dris_direct_descendant (
    child_code VARCHAR(8) NOT NULL PRIMARY KEY,
    child_node_name VARCHAR(64) NOT NULL,
    child_region_code VARCHAR(4) NOT NULL,
    child_org_code VARCHAR(4) NOT NULL,
    child_level VARCHAR(16) NOT NULL,
    child_drirp_url VARCHAR(255) NOT NULL,
    relation_status VARCHAR(16) NOT NULL DEFAULT 'PENDING',
    health_status VARCHAR(16) NOT NULL DEFAULT 'UNKNOWN',
    first_seen_at TIMESTAMP NULL,
    last_seen_at TIMESTAMP NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_direct_child_node_name (child_node_name),
    UNIQUE KEY uk_direct_child_code (child_region_code, child_org_code),
    KEY idx_direct_relation_health (relation_status, health_status),
    KEY idx_direct_last_seen (last_seen_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS dris_route_forward (
    target_code VARCHAR(8) NOT NULL PRIMARY KEY,
    target_region_code VARCHAR(4) NOT NULL,
    target_org_code VARCHAR(4) NOT NULL,
    target_node_name VARCHAR(64) NOT NULL,
    target_level VARCHAR(16) NOT NULL,
    next_hop_code VARCHAR(8) NOT NULL,
    next_hop_drirp_url VARCHAR(255) NOT NULL,
    route_type VARCHAR(16) NOT NULL DEFAULT 'DESCENDANT',
    hop_count INT NOT NULL DEFAULT 2,
    route_version BIGINT NOT NULL DEFAULT 1,
    source_node_name VARCHAR(64) DEFAULT '',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_route_target_code (target_region_code, target_org_code),
    KEY idx_route_next_hop_code (next_hop_code),
    KEY idx_route_target_node_name (target_node_name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

INSERT IGNORE INTO iam_user (user_id, username, display_name, org_code, password_hash, status)
VALUES ('u_admin', 'admin', 'Platform Admin', 'ABCD', '', 'active');

INSERT IGNORE INTO iam_user_role (user_id, role_code)
VALUES ('u_admin', 'admin');




