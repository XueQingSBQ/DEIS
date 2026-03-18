package com.dris.v2.backend.dao;

import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class IdentifierPermissionDao {
    private final JdbcTemplate jdbcTemplate;

    public IdentifierPermissionDao(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public int assignCreatorUserId(String identifier, String userId) {
        return jdbcTemplate.update(
                "UPDATE identifier_metadata SET creator_user_id = ? WHERE identifier = ?",
                userId,
                identifier
        );
    }

    public String findCreatorUserId(String identifier) {
        try {
            return jdbcTemplate.queryForObject(
                    "SELECT creator_user_id FROM identifier_metadata WHERE identifier = ?",
                    String.class,
                    identifier
            );
        } catch (EmptyResultDataAccessException ex) {
            return null;
        }
    }

    public String findIdentifierByUploadId(String uploadId) {
        try {
            return jdbcTemplate.queryForObject(
                    "SELECT identifier FROM upload_session WHERE upload_id = ?",
                    String.class,
                    uploadId
            );
        } catch (EmptyResultDataAccessException ex) {
            return null;
        }
    }
}
