package com.dris.v2.backend.dao;

import com.dris.v2.backend.model.RouteForwardRecord;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.util.List;

@Repository
public class RouteForwardDao {
    private final JdbcTemplate jdbcTemplate;

    public RouteForwardDao(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public boolean existsByCode(String regionCode, String orgCode) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(1) FROM dris_route_forward WHERE target_region_code = ? AND target_org_code = ?",
                Integer.class,
                regionCode,
                orgCode
        );
        return count != null && count > 0;
    }

    private static String composeCode(String regionCode, String orgCode) {
        return (regionCode == null ? "" : regionCode.trim()) + (orgCode == null ? "" : orgCode.trim());
    }

    public RouteForwardRecord findByTargetCode(String targetCode) {
        List<RouteForwardRecord> rows = jdbcTemplate.query(
                "SELECT target_code, target_region_code, target_org_code, target_node_name, target_level, next_hop_code, next_hop_drirp_url, hop_count, route_type, route_version, source_node_name, updated_at " +
                        "FROM dris_route_forward WHERE target_code = ? LIMIT 1",
                (rs, rowNum) -> {
                    RouteForwardRecord record = new RouteForwardRecord();
                    record.setTargetCode(rs.getString("target_code"));
                    record.setTargetRegionCode(rs.getString("target_region_code"));
                    record.setTargetOrgCode(rs.getString("target_org_code"));
                    record.setTargetNodeName(rs.getString("target_node_name"));
                    record.setTargetLevel(rs.getString("target_level"));
                    record.setNextHopCode(rs.getString("next_hop_code"));
                    record.setNextHopDrirpUrl(rs.getString("next_hop_drirp_url"));
                    record.setHopCount(rs.getInt("hop_count"));
                    record.setRouteType(rs.getString("route_type"));
                    record.setRouteVersion(rs.getLong("route_version"));
                    record.setSourceNodeName(rs.getString("source_node_name"));
                    Timestamp updatedAt = rs.getTimestamp("updated_at");
                    record.setUpdatedAt(updatedAt == null ? null : updatedAt.toInstant());
                    return record;
                },
                targetCode
        );
        return rows.isEmpty() ? null : rows.get(0);
    }

    public void upsertRoute(String targetRegionCode,
                            String targetOrgCode,
                            String targetNodeName,
                            String targetLevel,
                            String nextHopCode,
                            String nextHopDrirpUrl,
                            int hopCount,
                            String routeType,
                            String sourceNodeName) {
        String targetCode = composeCode(targetRegionCode, targetOrgCode);
        Integer count = jdbcTemplate.queryForObject("SELECT COUNT(1) FROM dris_route_forward WHERE target_code = ?", Integer.class, targetCode);
        if (count != null && count > 0) {
            jdbcTemplate.update(
                    "UPDATE dris_route_forward SET target_region_code = ?, target_org_code = ?, target_node_name = ?, target_level = ?, next_hop_code = ?, next_hop_drirp_url = ?, hop_count = ?, " +
                            "route_type = ?, source_node_name = ?, route_version = route_version + 1 WHERE target_code = ?",
                    targetRegionCode,
                    targetOrgCode,
                    targetNodeName,
                    targetLevel,
                    nextHopCode,
                    nextHopDrirpUrl,
                    hopCount,
                    routeType,
                    sourceNodeName == null ? "" : sourceNodeName,
                    targetCode
            );
            return;
        }
        jdbcTemplate.update(
                "INSERT INTO dris_route_forward (target_code, target_region_code, target_org_code, target_node_name, target_level, next_hop_code, next_hop_drirp_url, hop_count, route_type, source_node_name) " +
                        "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                targetCode,
                targetRegionCode,
                targetOrgCode,
                targetNodeName,
                targetLevel,
                nextHopCode,
                nextHopDrirpUrl,
                hopCount,
                routeType,
                sourceNodeName == null ? "" : sourceNodeName
        );
    }

    public List<RouteForwardRecord> listAll() {
        return jdbcTemplate.query(
                "SELECT target_code, target_region_code, target_org_code, target_node_name, target_level, next_hop_code, next_hop_drirp_url, hop_count, route_type, route_version, source_node_name, updated_at " +
                        "FROM dris_route_forward ORDER BY updated_at DESC",
                (rs, rowNum) -> {
                    RouteForwardRecord record = new RouteForwardRecord();
                    record.setTargetCode(rs.getString("target_code"));
                    record.setTargetRegionCode(rs.getString("target_region_code"));
                    record.setTargetOrgCode(rs.getString("target_org_code"));
                    record.setTargetNodeName(rs.getString("target_node_name"));
                    record.setTargetLevel(rs.getString("target_level"));
                    record.setNextHopCode(rs.getString("next_hop_code"));
                    record.setNextHopDrirpUrl(rs.getString("next_hop_drirp_url"));
                    record.setHopCount(rs.getInt("hop_count"));
                    record.setRouteType(rs.getString("route_type"));
                    record.setRouteVersion(rs.getLong("route_version"));
                    record.setSourceNodeName(rs.getString("source_node_name"));
                    Timestamp updatedAt = rs.getTimestamp("updated_at");
                    record.setUpdatedAt(updatedAt == null ? null : updatedAt.toInstant());
                    return record;
                }
        );
    }
}
