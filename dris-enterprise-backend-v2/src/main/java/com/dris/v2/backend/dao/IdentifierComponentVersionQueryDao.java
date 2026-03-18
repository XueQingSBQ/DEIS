package com.dris.v2.backend.dao;

import com.dris.v2.backend.model.IdentifierComponentVersionRecord;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public class IdentifierComponentVersionQueryDao {
    private final JdbcTemplate jdbcTemplate;

    public IdentifierComponentVersionQueryDao(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public long countByIdentifier(String identifier) {
        Long count = jdbcTemplate.queryForObject(
                "SELECT COUNT(1) FROM resource_version WHERE identifier=?",
                Long.class,
                identifier
        );
        return count == null ? 0L : count.longValue();
    }

    public List<IdentifierComponentVersionRecord> findPage(String identifier, int offset, int limit) {
        return jdbcTemplate.query(
                "SELECT id, identifier, version_no, component_type, storage_type, access_method, content_type, format, locator, size_bytes, hash_value, etag, provider_version_id, source_upload_id, bind_mode, verify_status, created_at, extra_json FROM resource_version WHERE identifier=? ORDER BY version_no DESC LIMIT ? OFFSET ?",
                new Object[]{identifier, Integer.valueOf(limit), Integer.valueOf(offset)},
                (rs, rowNum) -> mapRow(rs)
        );
    }

    public IdentifierComponentVersionRecord findOne(String identifier, long versionNo) {
        List<IdentifierComponentVersionRecord> rows = jdbcTemplate.query(
                "SELECT id, identifier, version_no, component_type, storage_type, access_method, content_type, format, locator, size_bytes, hash_value, etag, provider_version_id, source_upload_id, bind_mode, verify_status, created_at, extra_json FROM resource_version WHERE identifier=? AND version_no=? LIMIT 1",
                new Object[]{identifier, Long.valueOf(versionNo)},
                (rs, rowNum) -> mapRow(rs)
        );
        return rows.isEmpty() ? null : rows.get(0);
    }

    private IdentifierComponentVersionRecord mapRow(java.sql.ResultSet rs) throws java.sql.SQLException {
        IdentifierComponentVersionRecord record = new IdentifierComponentVersionRecord();
        record.setId(rs.getLong("id"));
        record.setIdentifier(rs.getString("identifier"));
        record.setVersionNo(rs.getLong("version_no"));
        record.setComponentType(rs.getString("component_type"));
        record.setStorageType(rs.getString("storage_type"));
        record.setAccessMethod(rs.getString("access_method"));
        record.setContentType(rs.getString("content_type"));
        record.setFormat(rs.getString("format"));
        record.setLocator(rs.getString("locator"));
        record.setSizeBytes(rs.getLong("size_bytes"));
        record.setHashValue(rs.getString("hash_value"));
        record.setEtag(rs.getString("etag"));
        record.setProviderVersionId(rs.getString("provider_version_id"));
        record.setSourceUploadId(rs.getString("source_upload_id"));
        record.setBindMode(rs.getString("bind_mode"));
        record.setVerifyStatus(rs.getString("verify_status"));
        record.setCreatedAt(rs.getString("created_at"));
        record.setExtraJson(rs.getString("extra_json"));
        return record;
    }
}
