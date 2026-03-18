package com.dris.v2.backend.service.local.upload;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Repository
public class UploadSessionDao {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final JdbcTemplate jdbcTemplate;

    public UploadSessionDao(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public UploadSessionRecord findByUploadId(String uploadId) {
        List<UploadSessionRecord> rows = jdbcTemplate.query(
                "SELECT upload_id, identifier, component_type, bucket, object_key, upload_mode, expected_size, expected_sha256, status, expires_at, extra_json FROM upload_session WHERE upload_id=? LIMIT 1",
                new Object[]{uploadId},
                (rs, rowNum) -> {
                    UploadSessionRecord record = new UploadSessionRecord();
                    record.setUploadId(rs.getString("upload_id"));
                    record.setIdentifier(rs.getString("identifier"));
                    record.setComponentType(rs.getString("component_type"));
                    record.setBucket(rs.getString("bucket"));
                    record.setObjectKey(rs.getString("object_key"));
                    record.setUploadMode(rs.getString("upload_mode"));
                    record.setExpectedSize(rs.getLong("expected_size"));
                    record.setExpectedSha256(rs.getString("expected_sha256"));
                    record.setStatus(rs.getString("status"));
                    record.setExpiresAt(rs.getString("expires_at"));
                    record.setExtra(fromJson(rs.getString("extra_json")));
                    return record;
                }
        );
        return rows.isEmpty() ? null : rows.get(0);
    }

    public void upsert(UploadSessionRecord record) {
        jdbcTemplate.update(
                "INSERT INTO upload_session (upload_id, identifier, component_type, bucket, object_key, upload_mode, expected_size, expected_sha256, status, expires_at, extra_json) VALUES (?,?,?,?,?,?,?,?,?,?,?) ON DUPLICATE KEY UPDATE identifier=VALUES(identifier), component_type=VALUES(component_type), bucket=VALUES(bucket), object_key=VALUES(object_key), upload_mode=VALUES(upload_mode), expected_size=VALUES(expected_size), expected_sha256=VALUES(expected_sha256), status=VALUES(status), expires_at=VALUES(expires_at), extra_json=VALUES(extra_json)",
                safe(record.getUploadId()),
                safe(record.getIdentifier()),
                safe(record.getComponentType()),
                safe(record.getBucket()),
                safe(record.getObjectKey()),
                safe(record.getUploadMode()),
                Long.valueOf(record.getExpectedSize()),
                safe(record.getExpectedSha256()),
                safe(record.getStatus()),
                safe(record.getExpiresAt()),
                toJson(record.getExtra())
        );
    }

    private Map<String, Object> fromJson(String json) {
        if (json == null || json.trim().isEmpty()) {
            return new LinkedHashMap<String, Object>();
        }
        try {
            Map<String, Object> value = OBJECT_MAPPER.readValue(json, new TypeReference<Map<String, Object>>() {
            });
            return value == null ? new LinkedHashMap<String, Object>() : new LinkedHashMap<String, Object>(value);
        } catch (Exception exception) {
            return new LinkedHashMap<String, Object>();
        }
    }

    private String toJson(Map<String, Object> value) {
        try {
            return OBJECT_MAPPER.writeValueAsString(value == null ? new LinkedHashMap<String, Object>() : value);
        } catch (Exception exception) {
            return "{}";
        }
    }

    private String safe(String value) {
        return value == null ? "" : value.trim();
    }
}