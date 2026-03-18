package com.dris.v2.backend.dao;

import com.dris.v2.backend.model.IdentifierHistoryRecord;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Repository
public class IdentifierHistoryQueryDao {
    private final JdbcTemplate jdbcTemplate;

    public IdentifierHistoryQueryDao(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public long count(String identifier, String operation, Instant startTime, Instant endTime) {
        QueryParts query = buildQueryParts(identifier, operation, startTime, endTime);
        Long count = jdbcTemplate.queryForObject(
                "SELECT COUNT(1) FROM identifier_history" + query.whereClause,
                Long.class,
                query.args.toArray()
        );
        return count == null ? 0L : count.longValue();
    }

    public List<IdentifierHistoryRecord> findPage(
            String identifier,
            String operation,
            Instant startTime,
            Instant endTime,
            int offset,
            int limit
    ) {
        QueryParts query = buildQueryParts(identifier, operation, startTime, endTime);
        List<Object> args = new ArrayList<Object>(query.args);
        args.add(Integer.valueOf(limit));
        args.add(Integer.valueOf(offset));
        return jdbcTemplate.query(
                "SELECT id, identifier, operation, actor_user_id, actor_role, success, message, created_at " +
                        "FROM identifier_history" + query.whereClause +
                        " ORDER BY created_at DESC, id DESC LIMIT ? OFFSET ?",
                args.toArray(),
                ROW_MAPPER
        );
    }

    private QueryParts buildQueryParts(String identifier, String operation, Instant startTime, Instant endTime) {
        StringBuilder where = new StringBuilder(" WHERE 1=1");
        List<Object> args = new ArrayList<Object>();
        if (!isBlank(identifier)) {
            where.append(" AND identifier = ?");
            args.add(identifier);
        }
        if (!isBlank(operation)) {
            where.append(" AND operation = ?");
            args.add(operation);
        }
        if (startTime != null) {
            where.append(" AND created_at >= ?");
            args.add(Timestamp.from(startTime));
        }
        if (endTime != null) {
            where.append(" AND created_at <= ?");
            args.add(Timestamp.from(endTime));
        }
        return new QueryParts(where.toString(), args);
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private static final RowMapper<IdentifierHistoryRecord> ROW_MAPPER = (rs, rowNum) -> {
        IdentifierHistoryRecord record = new IdentifierHistoryRecord();
        record.setId(Long.valueOf(rs.getLong("id")));
        record.setIdentifier(rs.getString("identifier"));
        record.setOperation(rs.getString("operation"));
        record.setActorUserId(rs.getString("actor_user_id"));
        record.setActorRole(rs.getString("actor_role"));
        record.setSuccess(rs.getInt("success") == 1);
        record.setMessage(rs.getString("message"));
        Timestamp createdAt = rs.getTimestamp("created_at");
        record.setCreatedAt(createdAt == null ? null : createdAt.toInstant());
        return record;
    };

    private static final class QueryParts {
        private final String whereClause;
        private final List<Object> args;

        private QueryParts(String whereClause, List<Object> args) {
            this.whereClause = whereClause;
            this.args = args;
        }
    }
}
