package com.dris.v2.backend.sdknext;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.dris.sdk.next.domain.Resource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.LinkedHashMap;
import java.util.Map;

@Repository
public class ResourceVersionWriteDao {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final JdbcTemplate jdbcTemplate;

    public ResourceVersionWriteDao(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public long nextVersionNo(String identifier) {
        Long value = jdbcTemplate.queryForObject(
                "SELECT COALESCE(MAX(version_no), 0) FROM resource_version WHERE identifier=?",
                Long.class,
                identifier
        );
        return value == null ? 1L : value.longValue() + 1L;
    }

    public void insert(String identifier, Resource resource) {
        long versionNo = nextVersionNo(identifier);
        Map<String, Object> extra = safeExtra(resource == null ? null : resource.getExtra());
        extra.put("_versionNo", Long.valueOf(versionNo));
        String sourceUploadId = firstNonBlank(extra.get("_sourceUploadId"), extra.get("uploadId"));
        String verifyStatus = firstNonBlank(extra.get("_verifyStatus"), "DECLARED");
        String bindMode = firstNonBlank(extra.get("_bindMode"), "direct");
        jdbcTemplate.update(
                "INSERT INTO resource_version (identifier, version_no, component_type, storage_type, access_method, content_type, format, locator, size_bytes, hash_value, etag, provider_version_id, source_upload_id, bind_mode, verify_status, created_at, extra_json) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
                identifier,
                Long.valueOf(versionNo),
                safe(resource.getComponentType()),
                safe(resource.getStorageType()),
                safe(resource.getAccessMethod()),
                safe(resource.getContentType()),
                safe(resource.getFormat()),
                safe(resource.getLocator()),
                Long.valueOf(resource.getSize()),
                safe(resource.getHashValue()),
                safe(resource.getEtag()),
                safe(resource.getVersionId()),
                sourceUploadId,
                bindMode,
                verifyStatus,
                safe(resource.getCreatedAt()),
                toJson(extra)
        );
    }

    private Map<String, Object> safeExtra(Map<String, Object> extra) {
        return extra == null ? new LinkedHashMap<String, Object>() : new LinkedHashMap<String, Object>(extra);
    }

    private String toJson(Map<String, Object> value) {
        try {
            return OBJECT_MAPPER.writeValueAsString(value == null ? new LinkedHashMap<String, Object>() : value);
        } catch (Exception exception) {
            return "{}";
        }
    }

    @SuppressWarnings("unchecked")
    private String firstNonBlank(Object first, Object fallback) {
        String primary = first == null ? "" : String.valueOf(first).trim();
        if (!primary.isEmpty()) {
            return primary;
        }
        return fallback == null ? "" : String.valueOf(fallback).trim();
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }

}
