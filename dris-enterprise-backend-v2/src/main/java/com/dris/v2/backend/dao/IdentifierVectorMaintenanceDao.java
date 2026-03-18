package com.dris.v2.backend.dao;

import com.dris.sdk.next.domain.IdentifierMetadata;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.List;

@Repository
public class IdentifierVectorMaintenanceDao {
    private final JdbcTemplate jdbcTemplate;

    public IdentifierVectorMaintenanceDao(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<IdentifierMetadata> findVectorDirty() {
        return jdbcTemplate.query(
                "SELECT identifier, region_code, organization_code, industry, data_category, registration_type, date, owner, description, application_scenario, name, entity_type, status, create_time, update_time FROM identifier_metadata WHERE vector_dirty=1 ORDER BY update_time ASC, identifier ASC LIMIT 1000",
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
                    return metadata;
                }
        );
    }

    public boolean clearVectorDirtyBatch(List<String> identifiers) {
        if (identifiers == null || identifiers.isEmpty()) {
            return true;
        }
        StringBuilder sql = new StringBuilder("UPDATE identifier_metadata SET vector_dirty=0 WHERE identifier IN (");
        List<Object> args = new ArrayList<Object>();
        for (int i = 0; i < identifiers.size(); i++) {
            if (i > 0) {
                sql.append(',');
            }
            sql.append('?');
            args.add(identifiers.get(i));
        }
        sql.append(')');
        jdbcTemplate.update(sql.toString(), args.toArray());
        return true;
    }

    public long countActiveIdentifiers() {
        return count("SELECT COUNT(1) FROM identifier_metadata WHERE status!='deleted'");
    }

    public long countDeletedIdentifiers() {
        return count("SELECT COUNT(1) FROM identifier_metadata WHERE status='deleted'");
    }

    public long countVectorDirty() {
        return count("SELECT COUNT(1) FROM identifier_metadata WHERE vector_dirty=1");
    }

    public long countVectorIndexed() {
        return count("SELECT COUNT(1) FROM identifier_vector_index");
    }

    private long count(String sql) {
        Long value = jdbcTemplate.queryForObject(sql, Long.class);
        return value == null ? 0L : value.longValue();
    }
}