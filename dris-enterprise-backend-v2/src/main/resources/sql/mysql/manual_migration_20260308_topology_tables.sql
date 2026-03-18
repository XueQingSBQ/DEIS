RENAME TABLE dris_db.dris_direct_descendant TO dris_db.dris_direct_descendant_legacy_20260308;
RENAME TABLE dris_db.dris_route_forward TO dris_db.dris_route_forward_legacy_20260308;

CREATE TABLE dris_db.dris_direct_descendant (
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

CREATE TABLE dris_db.dris_route_forward (
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