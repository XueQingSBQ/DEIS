package com.dris.v2.backend.dao;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class DrirpAuditLogDao {
    private final JdbcTemplate jdbcTemplate;

    public DrirpAuditLogDao(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public int insert(
            String traceId,
            String userId,
            String roleCode,
            String method,
            String path,
            int status,
            long durationMs,
            boolean success,
            String errorMessage
    ) {
        return jdbcTemplate.update(
                "INSERT INTO api_audit_log (trace_id, user_id, role_code, method, path, status, duration_ms, success, error_message) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)",
                traceId,
                userId,
                roleCode,
                method,
                path,
                Integer.valueOf(status),
                Long.valueOf(Math.max(0L, durationMs)),
                Integer.valueOf(success ? 1 : 0),
                errorMessage
        );
    }
}