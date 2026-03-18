package com.dris.v2.backend.sdknext;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.dris.sdk.next.domain.DigitalObject;
import com.dris.sdk.next.domain.IdentifierMetadata;
import com.dris.sdk.next.domain.Resource;
import com.dris.sdk.next.enumtype.ObjectStatus;
import com.dris.sdk.next.exception.ConflictException;
import com.dris.sdk.next.repository.DigitalObjectRepository;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Repository
public class JdbcNextDigitalObjectRepository implements DigitalObjectRepository {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final JdbcTemplate jdbcTemplate;

    public JdbcNextDigitalObjectRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public Optional<DigitalObject> findByIdentifier(String identifier) {
        List<DigitalObject> rows = jdbcTemplate.query(
                "SELECT m.identifier, m.region_code, m.organization_code, m.industry, m.data_category, m.registration_type, m.date, m.owner, m.description, m.application_scenario, m.name, m.entity_type, m.status, m.create_time, m.update_time, m.additional_attributes, m.revision, r.identifier AS r_identifier, r.component_type, r.storage_type, r.access_method, r.content_type, r.format, r.locator, r.size_bytes, r.hash_value, r.etag, r.version_id, r.created_at, r.extra_json FROM identifier_metadata m LEFT JOIN resource r ON m.identifier=r.identifier WHERE m.identifier=? LIMIT 1",
                new Object[]{identifier},
                (rs, rowNum) -> {
                    IdentifierMetadata metadata = new IdentifierMetadata();
                    metadata.setIdentifier(rs.getString("identifier"));
                    metadata.setRegionCode(rs.getString("region_code"));
                    metadata.setOrganizationCode(rs.getString("organization_code"));
                    metadata.setIndustry(rs.getString("industry"));
                    metadata.setDataCategory(rs.getString("data_category"));
                    metadata.setRegistrationType(rs.getString("registration_type"));
                    metadata.setDate(rs.getString("date"));
                    metadata.setOwner(rs.getString("owner"));
                    metadata.setDescription(rs.getString("description"));
                    metadata.setApplicationScenario(rs.getString("application_scenario"));
                    metadata.setName(rs.getString("name"));
                    metadata.setEntityType(rs.getString("entity_type"));
                    metadata.setStatus(rs.getString("status"));
                    metadata.setCreateTime(rs.getString("create_time"));
                    metadata.setUpdateTime(rs.getString("update_time"));
                    metadata.setAdditionalAttributes(fromJson(rs.getString("additional_attributes")));

                    DigitalObject object = new DigitalObject();
                    object.setIdentifier(metadata.getIdentifier());
                    object.setMetadata(metadata);
                    object.setStatus("deleted".equalsIgnoreCase(metadata.getStatus()) ? ObjectStatus.DELETED : ObjectStatus.ACTIVE);
                    object.setRevision(rs.getLong("revision"));
                    if (rs.getString("r_identifier") != null) {
                        Resource resource = new Resource();
                        resource.setComponentType(rs.getString("component_type"));
                        resource.setStorageType(rs.getString("storage_type"));
                        resource.setAccessMethod(rs.getString("access_method"));
                        resource.setContentType(rs.getString("content_type"));
                        resource.setFormat(rs.getString("format"));
                        resource.setLocator(rs.getString("locator"));
                        resource.setSize(rs.getLong("size_bytes"));
                        resource.setHashValue(rs.getString("hash_value"));
                        resource.setEtag(rs.getString("etag"));
                        resource.setVersionId(rs.getString("version_id"));
                        resource.setCreatedAt(rs.getString("created_at"));
                        resource.setExtra(fromJson(rs.getString("extra_json")));
                        object.setResource(resource);
                    }
                    return object;
                }
        );
        return rows.isEmpty() ? Optional.<DigitalObject>empty() : Optional.of(rows.get(0));
    }

    @Override
    public boolean existsByIdentifier(String identifier) {
        List<Integer> rows = jdbcTemplate.query(
                "SELECT 1 FROM identifier_metadata WHERE identifier=? LIMIT 1",
                new Object[]{identifier},
                (rs, rowNum) -> Integer.valueOf(1)
        );
        return !rows.isEmpty();
    }

    @Override
    public void insert(DigitalObject object) {
        IdentifierMetadata metadata = requiredMetadata(object);
        try {
            jdbcTemplate.update(
                    "INSERT INTO identifier_metadata (identifier, region_code, organization_code, industry, data_category, registration_type, date, owner, description, application_scenario, name, entity_type, status, create_time, update_time, additional_attributes, vector_dirty, revision) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,1,?)",
                    metadata.getIdentifier(),
                    metadata.getRegionCode(),
                    metadata.getOrganizationCode(),
                    metadata.getIndustry(),
                    metadata.getDataCategory(),
                    metadata.getRegistrationType(),
                    metadata.getDate(),
                    metadata.getOwner(),
                    safe(metadata.getDescription()),
                    safe(metadata.getApplicationScenario()),
                    safe(metadata.getName()),
                    safe(metadata.getEntityType()),
                    safe(metadata.getStatus()),
                    safe(metadata.getCreateTime()),
                    safe(metadata.getUpdateTime()),
                    toJson(metadata.getAdditionalAttributes()),
                    Long.valueOf(object.getRevision())
            );
            if (object.getResource() != null) {
                upsertResource(object.getIdentifier(), object.getResource());
            }
        } catch (DataAccessException exception) {
            throw new ConflictException("identifier already exists: " + object.getIdentifier());
        }
    }

    @Override
    public boolean update(DigitalObject object, long expectedRevision) {
        IdentifierMetadata metadata = requiredMetadata(object);
        int updated = jdbcTemplate.update(
                "UPDATE identifier_metadata SET region_code=?, organization_code=?, industry=?, data_category=?, registration_type=?, date=?, owner=?, description=?, application_scenario=?, name=?, entity_type=?, status=?, create_time=?, update_time=?, additional_attributes=?, vector_dirty=1, revision=? WHERE identifier=? AND revision=?",
                metadata.getRegionCode(),
                metadata.getOrganizationCode(),
                metadata.getIndustry(),
                metadata.getDataCategory(),
                metadata.getRegistrationType(),
                metadata.getDate(),
                metadata.getOwner(),
                safe(metadata.getDescription()),
                safe(metadata.getApplicationScenario()),
                safe(metadata.getName()),
                safe(metadata.getEntityType()),
                safe(metadata.getStatus()),
                safe(metadata.getCreateTime()),
                safe(metadata.getUpdateTime()),
                toJson(metadata.getAdditionalAttributes()),
                Long.valueOf(object.getRevision()),
                object.getIdentifier(),
                Long.valueOf(expectedRevision)
        );
        if (updated <= 0) {
            return false;
        }
        if (object.getResource() == null) {
            jdbcTemplate.update("DELETE FROM resource WHERE identifier=?", object.getIdentifier());
        } else {
            upsertResource(object.getIdentifier(), object.getResource());
        }
        return true;
    }

    private void upsertResource(String identifier, Resource resource) {
        jdbcTemplate.update(
                "INSERT INTO resource (identifier, component_type, storage_type, access_method, content_type, format, locator, size_bytes, hash_value, etag, version_id, created_at, extra_json) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?) ON DUPLICATE KEY UPDATE component_type=VALUES(component_type), storage_type=VALUES(storage_type), access_method=VALUES(access_method), content_type=VALUES(content_type), format=VALUES(format), locator=VALUES(locator), size_bytes=VALUES(size_bytes), hash_value=VALUES(hash_value), etag=VALUES(etag), version_id=VALUES(version_id), created_at=VALUES(created_at), extra_json=VALUES(extra_json)",
                identifier,
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
                safe(resource.getCreatedAt()),
                toJson(resource.getExtra())
        );
    }

    private IdentifierMetadata requiredMetadata(DigitalObject object) {
        if (object == null || object.getMetadata() == null) {
            throw new IllegalArgumentException("metadata is required");
        }
        return object.getMetadata();
    }

    private Map<String, Object> fromJson(String json) {
        if (json == null || json.trim().isEmpty()) {
            return new LinkedHashMap<String, Object>();
        }
        try {
            Map<String, Object> value = OBJECT_MAPPER.readValue(json, new TypeReference<Map<String, Object>>() {
            });
            return value == null ? new LinkedHashMap<String, Object>() : value;
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
        return value == null ? "" : value;
    }

}
