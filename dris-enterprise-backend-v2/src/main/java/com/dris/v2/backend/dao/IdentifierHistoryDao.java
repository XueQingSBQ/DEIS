package com.dris.v2.backend.dao;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class IdentifierHistoryDao {
    private final JdbcTemplate jdbcTemplate;

    public IdentifierHistoryDao(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public int insert(
            String identifier,
            String operation,
            String actorUserId,
            String actorRole,
            boolean success,
            String message
    ) {
        return jdbcTemplate.update(
                "INSERT INTO identifier_history (identifier, operation, actor_user_id, actor_role, success, message) VALUES (?, ?, ?, ?, ?, ?)",
                identifier,
                operation,
                actorUserId,
                actorRole,
                Integer.valueOf(success ? 1 : 0),
                message
        );
    }
}
