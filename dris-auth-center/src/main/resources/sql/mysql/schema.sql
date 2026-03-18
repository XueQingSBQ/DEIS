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

INSERT IGNORE INTO iam_user (user_id, username, display_name, org_code, password_hash, status)
VALUES ('u_admin', 'admin', 'Platform Admin', 'ABCD', '', 'active');

ALTER TABLE iam_user
    ADD COLUMN password_hash VARCHAR(255) NOT NULL DEFAULT '';
