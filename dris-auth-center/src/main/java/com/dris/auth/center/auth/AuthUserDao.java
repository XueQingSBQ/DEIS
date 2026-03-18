package com.dris.auth.center.auth;

import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

@Repository
public class AuthUserDao {
    private static final RowMapper<AuthUser> ROW_MAPPER = (rs, rowNum) -> {
        AuthUser user = new AuthUser();
        user.setUserId(rs.getString("user_id"));
        user.setUsername(rs.getString("username"));
        user.setDisplayName(rs.getString("display_name"));
        user.setOrgCode(rs.getString("org_code"));
        user.setNodeId(rs.getString("node_id"));
        user.setEmail(rs.getString("email"));
        user.setPhone(rs.getString("phone"));
        user.setPasswordHash(rs.getString("password_hash"));
        user.setStatus(rs.getString("status"));
        return user;
    };

    private final JdbcTemplate jdbcTemplate;

    public AuthUserDao(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public AuthUser findByUsername(String username) {
        try {
            return jdbcTemplate.queryForObject(
                    "SELECT user_id, username, display_name, org_code, node_id, email, phone, password_hash, status FROM iam_user WHERE username = ?",
                    ROW_MAPPER,
                    username
            );
        } catch (EmptyResultDataAccessException ex) {
            return null;
        }
    }

    public int insert(AuthUser user) {
        return jdbcTemplate.update(
                "INSERT INTO iam_user (user_id, username, display_name, org_code, node_id, email, phone, password_hash, status) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)",
                user.getUserId(),
                user.getUsername(),
                user.getDisplayName(),
                user.getOrgCode(),
                blankIfNull(user.getNodeId()),
                blankIfNull(user.getEmail()),
                blankIfNull(user.getPhone()),
                user.getPasswordHash(),
                blankIfNull(user.getStatus(), "active")
        );
    }

    private static String blankIfNull(String value) {
        return value == null ? "" : value;
    }

    private static String blankIfNull(String value, String fallback) {
        if (value == null || value.trim().isEmpty()) {
            return fallback;
        }
        return value;
    }
}
