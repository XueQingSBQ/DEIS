package com.dris.v2.backend.dao;

import com.dris.v2.backend.model.DirectDescendantRecord;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;

@Repository
public class DirectDescendantDao {
    private final JdbcTemplate jdbcTemplate;

    public DirectDescendantDao(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public boolean existsByCode(String regionCode, String orgCode) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(1) FROM dris_direct_descendant WHERE child_region_code = ? AND child_org_code = ?",
                Integer.class,
                regionCode,
                orgCode
        );
        return count != null && count > 0;
    }

    private static String composeCode(String regionCode, String orgCode) {
        return (regionCode == null ? "" : regionCode.trim()) + (orgCode == null ? "" : orgCode.trim());
    }

    public DirectDescendantRecord findByChildCode(String childCode) {
        List<DirectDescendantRecord> rows = jdbcTemplate.query(
                "SELECT child_code, child_node_name, child_region_code, child_org_code, child_level, child_drirp_url, relation_status, health_status, first_seen_at, last_seen_at " +
                        "FROM dris_direct_descendant WHERE child_code = ?",
                (rs, rowNum) -> {
                    DirectDescendantRecord record = new DirectDescendantRecord();
                    record.setChildCode(rs.getString("child_code"));
                    record.setChildNodeName(rs.getString("child_node_name"));
                    record.setChildRegionCode(rs.getString("child_region_code"));
                    record.setChildOrgCode(rs.getString("child_org_code"));
                    record.setChildLevel(rs.getString("child_level"));
                    record.setChildDrirpUrl(rs.getString("child_drirp_url"));
                    record.setRelationStatus(rs.getString("relation_status"));
                    record.setHealthStatus(rs.getString("health_status"));
                    Timestamp firstSeen = rs.getTimestamp("first_seen_at");
                    Timestamp lastSeen = rs.getTimestamp("last_seen_at");
                    record.setFirstSeenAt(firstSeen == null ? null : firstSeen.toInstant());
                    record.setLastSeenAt(lastSeen == null ? null : lastSeen.toInstant());
                    return record;
                },
                childCode
        );
        return rows.isEmpty() ? null : rows.get(0);
    }
    public DirectDescendantRecord findByChildNodeName(String childNodeName) {
        List<DirectDescendantRecord> rows = jdbcTemplate.query(
                "SELECT child_code, child_node_name, child_region_code, child_org_code, child_level, child_drirp_url, relation_status, health_status, first_seen_at, last_seen_at " +
                        "FROM dris_direct_descendant WHERE child_node_name = ?",
                (rs, rowNum) -> {
                    DirectDescendantRecord record = new DirectDescendantRecord();
                    record.setChildCode(rs.getString("child_code"));
                    record.setChildNodeName(rs.getString("child_node_name"));
                    record.setChildRegionCode(rs.getString("child_region_code"));
                    record.setChildOrgCode(rs.getString("child_org_code"));
                    record.setChildLevel(rs.getString("child_level"));
                    record.setChildDrirpUrl(rs.getString("child_drirp_url"));
                    record.setRelationStatus(rs.getString("relation_status"));
                    record.setHealthStatus(rs.getString("health_status"));
                    Timestamp firstSeen = rs.getTimestamp("first_seen_at");
                    Timestamp lastSeen = rs.getTimestamp("last_seen_at");
                    record.setFirstSeenAt(firstSeen == null ? null : firstSeen.toInstant());
                    record.setLastSeenAt(lastSeen == null ? null : lastSeen.toInstant());
                    return record;
                },
                childNodeName
        );
        return rows.isEmpty() ? null : rows.get(0);
    }

    public void upsertPending(String childNodeName, String regionCode, String orgCode, String childLevel, String childDrirpUrl) {
        String childCode = composeCode(regionCode, orgCode);
        DirectDescendantRecord existing = findByChildCode(childCode);
        if (existing != null) {
            jdbcTemplate.update(
                    "UPDATE dris_direct_descendant SET child_node_name = ?, child_region_code = ?, child_org_code = ?, child_level = ?, child_drirp_url = ?, relation_status = 'PENDING', health_status = 'UNKNOWN' " +
                            "WHERE child_code = ?",
                    childNodeName,
                    regionCode,
                    orgCode,
                    childLevel,
                    childDrirpUrl,
                    childCode
            );
            return;
        }

        DirectDescendantRecord existingByName = findByChildNodeName(childNodeName);
        if (existingByName != null) {
            jdbcTemplate.update(
                    "UPDATE dris_direct_descendant SET child_code = ?, child_region_code = ?, child_org_code = ?, child_level = ?, child_drirp_url = ?, relation_status = 'PENDING', health_status = 'UNKNOWN' " +
                            "WHERE child_node_name = ?",
                    childCode,
                    regionCode,
                    orgCode,
                    childLevel,
                    childDrirpUrl,
                    childNodeName
            );
            return;
        }

        jdbcTemplate.update(
                "INSERT INTO dris_direct_descendant (child_code, child_node_name, child_region_code, child_org_code, child_level, child_drirp_url, relation_status, health_status) " +
                        "VALUES (?, ?, ?, ?, ?, ?, 'PENDING', 'UNKNOWN')",
                childCode,
                childNodeName,
                regionCode,
                orgCode,
                childLevel,
                childDrirpUrl
        );
    }

    public ActivationResult activateOnHello(String childCode, String childDrirpUrl) {
        DirectDescendantRecord existing = findByChildCode(childCode);
        if (existing == null) {
            return null;
        }
        boolean first = existing.getFirstSeenAt() == null;
        Instant now = Instant.now();
        if (first) {
            jdbcTemplate.update(
                    "UPDATE dris_direct_descendant SET child_drirp_url = ?, relation_status = 'ACTIVE', health_status = 'ACTIVE', first_seen_at = ?, last_seen_at = ? " +
                            "WHERE child_code = ?",
                    childDrirpUrl,
                    Timestamp.from(now),
                    Timestamp.from(now),
                    childCode
            );
        } else {
            jdbcTemplate.update(
                    "UPDATE dris_direct_descendant SET child_drirp_url = ?, relation_status = 'ACTIVE', health_status = 'ACTIVE', last_seen_at = ? WHERE child_code = ?",
                    childDrirpUrl,
                    Timestamp.from(now),
                    childCode
            );
        }
        existing.setChildDrirpUrl(childDrirpUrl);
        existing.setRelationStatus("ACTIVE");
        existing.setHealthStatus("ACTIVE");
        if (first) {
            existing.setFirstSeenAt(now);
        }
        existing.setLastSeenAt(now);
        return new ActivationResult(first, existing);
    }

    public int touchActive(String childCode, Instant now) {
        if (childCode == null || childCode.trim().isEmpty()) {
            return 0;
        }
        Instant effectiveNow = now == null ? Instant.now() : now;
        return jdbcTemplate.update(
                "UPDATE dris_direct_descendant SET relation_status = 'ACTIVE', health_status = 'ACTIVE', last_seen_at = ? WHERE child_code = ?",
                Timestamp.from(effectiveNow),
                childCode.trim()
        );
    }

    public int markOffline(String childCode, Instant now) {
        if (childCode == null || childCode.trim().isEmpty()) {
            return 0;
        }
        return jdbcTemplate.update(
                "UPDATE dris_direct_descendant SET health_status = 'OFFLINE' WHERE child_code = ?",
                childCode.trim()
        );
    }

    public int markStaleOffline(Instant staleBefore) {
        if (staleBefore == null) {
            return 0;
        }
        return jdbcTemplate.update(
                "UPDATE dris_direct_descendant " +
                        "SET health_status = 'OFFLINE' " +
                        "WHERE UPPER(relation_status) = 'ACTIVE' " +
                        "  AND (last_seen_at IS NULL OR last_seen_at < ?) " +
                        "  AND UPPER(health_status) <> 'OFFLINE'",
                Timestamp.from(staleBefore)
        );
    }

    public List<DirectDescendantRecord> listAll() {
        return jdbcTemplate.query(
                "SELECT child_code, child_node_name, child_region_code, child_org_code, child_level, child_drirp_url, relation_status, health_status, first_seen_at, last_seen_at " +
                        "FROM dris_direct_descendant ORDER BY updated_at DESC",
                (rs, rowNum) -> {
                    DirectDescendantRecord record = new DirectDescendantRecord();
                    record.setChildCode(rs.getString("child_code"));
                    record.setChildNodeName(rs.getString("child_node_name"));
                    record.setChildRegionCode(rs.getString("child_region_code"));
                    record.setChildOrgCode(rs.getString("child_org_code"));
                    record.setChildLevel(rs.getString("child_level"));
                    record.setChildDrirpUrl(rs.getString("child_drirp_url"));
                    record.setRelationStatus(rs.getString("relation_status"));
                    record.setHealthStatus(rs.getString("health_status"));
                    Timestamp firstSeen = rs.getTimestamp("first_seen_at");
                    Timestamp lastSeen = rs.getTimestamp("last_seen_at");
                    record.setFirstSeenAt(firstSeen == null ? null : firstSeen.toInstant());
                    record.setLastSeenAt(lastSeen == null ? null : lastSeen.toInstant());
                    return record;
                }
        );
    }

    public List<DirectDescendantRecord> listActiveChildren() {
        return jdbcTemplate.query(
                "SELECT child_code, child_node_name, child_region_code, child_org_code, child_level, child_drirp_url, relation_status, health_status, first_seen_at, last_seen_at " +
                        "FROM dris_direct_descendant WHERE UPPER(relation_status)='ACTIVE' ORDER BY child_code ASC",
                (rs, rowNum) -> {
                    DirectDescendantRecord record = new DirectDescendantRecord();
                    record.setChildCode(rs.getString("child_code"));
                    record.setChildNodeName(rs.getString("child_node_name"));
                    record.setChildRegionCode(rs.getString("child_region_code"));
                    record.setChildOrgCode(rs.getString("child_org_code"));
                    record.setChildLevel(rs.getString("child_level"));
                    record.setChildDrirpUrl(rs.getString("child_drirp_url"));
                    record.setRelationStatus(rs.getString("relation_status"));
                    record.setHealthStatus(rs.getString("health_status"));
                    Timestamp firstSeen = rs.getTimestamp("first_seen_at");
                    Timestamp lastSeen = rs.getTimestamp("last_seen_at");
                    record.setFirstSeenAt(firstSeen == null ? null : firstSeen.toInstant());
                    record.setLastSeenAt(lastSeen == null ? null : lastSeen.toInstant());
                    return record;
                }
        );
    }

    public static final class ActivationResult {
        private final boolean firstActivation;
        private final DirectDescendantRecord record;

        public ActivationResult(boolean firstActivation, DirectDescendantRecord record) {
            this.firstActivation = firstActivation;
            this.record = record;
        }

        public boolean isFirstActivation() {
            return firstActivation;
        }

        public DirectDescendantRecord getRecord() {
            return record;
        }
    }
}
