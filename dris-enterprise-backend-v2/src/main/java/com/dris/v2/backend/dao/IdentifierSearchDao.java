package com.dris.v2.backend.dao;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Repository
public class IdentifierSearchDao {
    private final JdbcTemplate jdbcTemplate;

    public IdentifierSearchDao(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<SearchDocumentRow> searchFuzzy(
            List<String> tokens,
            List<String> fields,
            Map<String, String> filters,
            int limit,
            int offset
    ) {
        List<String> safeTokens = tokens == null ? Collections.<String>emptyList() : tokens;
        List<String> dbFields = mapFieldColumns(fields);
        Map<String, String> dbFilters = mapFilterColumns(filters);
        int safeLimit = Math.max(0, limit);
        int safeOffset = Math.max(0, offset);
        int requested = safeLimit + safeOffset;
        int fetchLimit = Math.min(Math.max(requested * 4, requested), 5000);
        if (requested <= 0) {
            return new ArrayList<SearchDocumentRow>();
        }

        SearchPrefetchOrder prefetchOrder = buildSearchPrefetchOrder(safeTokens, dbFields);
        StringBuilder sql = new StringBuilder(
                "SELECT m.identifier, m.name, m.description, m.application_scenario, m.owner, m.industry, m.data_category, " +
                        "m.registration_type, m.entity_type, m.status, m.update_time, " +
                        "r.component_type, r.storage_type, r.access_method, r.content_type, r.format, r.locator, " +
                        prefetchOrder.expression + " AS prefetch_score " +
                        "FROM identifier_metadata m LEFT JOIN resource r ON r.identifier=m.identifier WHERE m.status!='deleted'"
        );
        for (String col : dbFilters.keySet()) {
            sql.append(" AND ").append(col).append(" = ?");
        }
        if (!safeTokens.isEmpty() && !dbFields.isEmpty()) {
            sql.append(" AND (");
            for (int i = 0; i < safeTokens.size(); i++) {
                if (i > 0) {
                    sql.append(" OR ");
                }
                sql.append('(');
                for (int j = 0; j < dbFields.size(); j++) {
                    if (j > 0) {
                        sql.append(" OR ");
                    }
                    sql.append(dbFields.get(j)).append(" LIKE ?");
                }
                sql.append(')');
            }
            sql.append(')');
        }
        sql.append(" ORDER BY prefetch_score DESC, m.update_time DESC, m.identifier ASC LIMIT ?");

        List<Object> params = new ArrayList<Object>();
        params.addAll(prefetchOrder.params);
        params.addAll(dbFilters.values());
        if (!safeTokens.isEmpty() && !dbFields.isEmpty()) {
            for (String token : safeTokens) {
                String like = "%" + token + "%";
                for (int i = 0; i < dbFields.size(); i++) {
                    params.add(like);
                }
            }
        }
        params.add(Integer.valueOf(fetchLimit));

        List<SearchDocumentRow> rows = jdbcTemplate.query(sql.toString(), params.toArray(), (rs, rowNum) -> {
            SearchDocumentRow row = mapRow(rs);
            row.setScore(scoreFuzzy(
                    safeTokens,
                    row.getName(),
                    row.getDescription(),
                    row.getApplicationScenario(),
                    row.getIdentifier(),
                    row.getOwner(),
                    row.getIndustry(),
                    row.getDataCategory(),
                    row.getRegistrationType(),
                    row.getEntityType(),
                    row.getComponentType(),
                    row.getStorageType(),
                    row.getAccessMethod(),
                    row.getContentType(),
                    row.getFormat(),
                    row.getLocator()
            ));
            return row;
        });
        List<SearchDocumentRow> matched = new ArrayList<SearchDocumentRow>();
        for (SearchDocumentRow row : rows) {
            if (row != null && row.getScore() > 0.0) {
                matched.add(row);
            }
        }
        matched.sort(new Comparator<SearchDocumentRow>() {
            @Override
            public int compare(SearchDocumentRow a, SearchDocumentRow b) {
                return Double.compare(b.getScore(), a.getScore());
            }
        });
        int from = Math.min(safeOffset, matched.size());
        int to = Math.min(from + safeLimit, matched.size());
        return new ArrayList<SearchDocumentRow>(matched.subList(from, to));
    }

    public Map<String, SearchDocumentRow> findActiveByIdentifiers(List<String> identifiers) {
        Map<String, SearchDocumentRow> out = new LinkedHashMap<String, SearchDocumentRow>();
        if (identifiers == null || identifiers.isEmpty()) {
            return out;
        }
        StringBuilder sql = new StringBuilder(
                "SELECT m.identifier, m.name, m.description, m.application_scenario, m.owner, m.industry, m.data_category, m.registration_type, m.entity_type, m.status, m.update_time, r.component_type, r.storage_type, r.access_method, r.content_type, r.format, r.locator FROM identifier_metadata m LEFT JOIN resource r ON r.identifier=m.identifier WHERE m.status!='deleted' AND m.identifier IN ("
        );
        for (int i = 0; i < identifiers.size(); i++) {
            if (i > 0) {
                sql.append(',');
            }
            sql.append('?');
        }
        sql.append(')');
        List<SearchDocumentRow> rows = jdbcTemplate.query(sql.toString(), identifiers.toArray(), (rs, rowNum) -> mapRow(rs));
        for (SearchDocumentRow row : rows) {
            out.put(row.getIdentifier(), row);
        }
        return out;
    }

    public List<SearchDocumentRow> findAllActive() {
        return jdbcTemplate.query(
                "SELECT m.identifier, m.name, m.description, m.application_scenario, m.owner, m.industry, m.data_category, m.registration_type, m.entity_type, m.status, m.update_time, r.component_type, r.storage_type, r.access_method, r.content_type, r.format, r.locator FROM identifier_metadata m LEFT JOIN resource r ON r.identifier=m.identifier WHERE m.status!='deleted' ORDER BY m.update_time DESC, m.identifier ASC",
                (rs, rowNum) -> mapRow(rs)
        );
    }

    private SearchDocumentRow mapRow(java.sql.ResultSet rs) throws java.sql.SQLException {
        SearchDocumentRow row = new SearchDocumentRow();
        row.setIdentifier(rs.getString("identifier"));
        row.setName(rs.getString("name"));
        row.setDescription(rs.getString("description"));
        row.setApplicationScenario(rs.getString("application_scenario"));
        row.setOwner(rs.getString("owner"));
        row.setIndustry(rs.getString("industry"));
        row.setDataCategory(rs.getString("data_category"));
        row.setRegistrationType(rs.getString("registration_type"));
        row.setEntityType(rs.getString("entity_type"));
        row.setStatus(rs.getString("status"));
        row.setUpdateTime(rs.getString("update_time"));
        row.setComponentType(rs.getString("component_type"));
        row.setStorageType(rs.getString("storage_type"));
        row.setAccessMethod(rs.getString("access_method"));
        row.setContentType(rs.getString("content_type"));
        row.setFormat(rs.getString("format"));
        row.setLocator(rs.getString("locator"));
        return row;
    }

    private SearchPrefetchOrder buildSearchPrefetchOrder(List<String> tokens, List<String> dbFields) {
        if (tokens == null || tokens.isEmpty() || dbFields == null || dbFields.isEmpty()) {
            return new SearchPrefetchOrder("0.0", Collections.<String>emptyList());
        }
        StringBuilder expression = new StringBuilder();
        List<String> params = new ArrayList<String>();
        for (String token : tokens) {
            if (token == null || token.trim().isEmpty()) {
                continue;
            }
            if (expression.length() > 0) {
                expression.append(" + ");
            }
            String normalized = token.toLowerCase(Locale.ROOT);
            String like = "%" + normalized + "%";
            String prefix = normalized + "%";
            List<String> cases = new ArrayList<String>();
            appendPrefetchCase(cases, params, dbFields, "m.name", 3.0, like);
            appendPrefetchCase(cases, params, dbFields, "m.description", 2.0, like);
            appendPrefetchCase(cases, params, dbFields, "m.application_scenario", 1.0, like);
            appendIdentifierPrefetchCase(cases, params, dbFields, "m.identifier", 2.0, 1.5, like, prefix);
            appendPrefetchCase(cases, params, dbFields, "m.owner", 1.2, like);
            appendPrefetchCase(cases, params, dbFields, "m.industry", 1.2, like);
            appendPrefetchCase(cases, params, dbFields, "m.data_category", 1.2, like);
            appendPrefetchCase(cases, params, dbFields, "m.registration_type", 1.2, like);
            appendPrefetchCase(cases, params, dbFields, "m.entity_type", 1.0, like);
            appendPrefetchCase(cases, params, dbFields, "r.component_type", 1.3, like);
            appendPrefetchCase(cases, params, dbFields, "r.storage_type", 1.0, like);
            appendPrefetchCase(cases, params, dbFields, "r.access_method", 1.0, like);
            appendPrefetchCase(cases, params, dbFields, "r.content_type", 1.0, like);
            appendPrefetchCase(cases, params, dbFields, "r.format", 1.0, like);
            appendPrefetchCase(cases, params, dbFields, "r.locator", 0.8, like);
            if (cases.isEmpty()) {
                continue;
            }
            if (cases.size() == 1) {
                expression.append(cases.get(0));
            } else {
                expression.append("GREATEST(");
                for (int i = 0; i < cases.size(); i++) {
                    if (i > 0) {
                        expression.append(", ");
                    }
                    expression.append(cases.get(i));
                }
                expression.append(')');
            }
        }
        if (expression.length() == 0) {
            return new SearchPrefetchOrder("0.0", Collections.<String>emptyList());
        }
        return new SearchPrefetchOrder(expression.toString(), params);
    }

    private void appendPrefetchCase(
            List<String> cases,
            List<String> params,
            List<String> dbFields,
            String field,
            double score,
            String like
    ) {
        if (!dbFields.contains(field)) {
            return;
        }
        cases.add("CASE WHEN LOWER(COALESCE(" + field + ",'')) LIKE ? THEN " + Double.toString(score) + " ELSE 0 END");
        params.add(like);
    }

    private void appendIdentifierPrefetchCase(
            List<String> cases,
            List<String> params,
            List<String> dbFields,
            String field,
            double prefixScore,
            double containsScore,
            String like,
            String prefix
    ) {
        if (!dbFields.contains(field)) {
            return;
        }
        cases.add("CASE WHEN LOWER(COALESCE(" + field + ",'')) LIKE ? THEN " + Double.toString(prefixScore) +
                " WHEN LOWER(COALESCE(" + field + ",'')) LIKE ? THEN " + Double.toString(containsScore) + " ELSE 0 END");
        params.add(prefix);
        params.add(like);
    }

    private Map<String, String> mapFilterColumns(Map<String, String> filters) {
        Map<String, String> out = new LinkedHashMap<String, String>();
        if (filters == null || filters.isEmpty()) {
            return out;
        }
        for (Map.Entry<String, String> entry : filters.entrySet()) {
            if (entry.getKey() == null || entry.getValue() == null) {
                continue;
            }
            String key = entry.getKey().trim();
            String value = entry.getValue().trim();
            if (key.isEmpty() || value.isEmpty()) {
                continue;
            }
            if ("industry".equals(key)) {
                out.put("m.industry", value);
            } else if ("status".equals(key)) {
                out.put("m.status", value);
            } else if ("entityType".equals(key)) {
                out.put("m.entity_type", value);
            } else if ("dataCategory".equals(key)) {
                out.put("m.data_category", value);
            } else if ("owner".equals(key)) {
                out.put("m.owner", value);
            } else if ("registrationType".equals(key)) {
                out.put("m.registration_type", value);
            } else if ("componentType".equals(key)) {
                out.put("r.component_type", value);
            } else if ("storageType".equals(key)) {
                out.put("r.storage_type", value);
            } else if ("accessMethod".equals(key)) {
                out.put("r.access_method", value);
            } else if ("contentType".equals(key)) {
                out.put("r.content_type", value);
            } else if ("format".equals(key)) {
                out.put("r.format", value);
            }
        }
        return out;
    }

    private List<String> mapFieldColumns(List<String> fields) {
        List<String> out = new ArrayList<String>();
        if (fields != null) {
            for (String field : fields) {
                if (field == null) {
                    continue;
                }
                String value = field.trim().toLowerCase(Locale.ROOT);
                if ("name".equals(value)) {
                    out.add("m.name");
                } else if ("description".equals(value)) {
                    out.add("m.description");
                } else if ("applicationscenario".equals(value) || "application_scenario".equals(value)) {
                    out.add("m.application_scenario");
                } else if ("identifier".equals(value)) {
                    out.add("m.identifier");
                } else if ("owner".equals(value)) {
                    out.add("m.owner");
                } else if ("industry".equals(value)) {
                    out.add("m.industry");
                } else if ("datacategory".equals(value)) {
                    out.add("m.data_category");
                } else if ("registrationtype".equals(value)) {
                    out.add("m.registration_type");
                } else if ("entitytype".equals(value)) {
                    out.add("m.entity_type");
                } else if ("componenttype".equals(value)) {
                    out.add("r.component_type");
                } else if ("storagetype".equals(value)) {
                    out.add("r.storage_type");
                } else if ("accessmethod".equals(value)) {
                    out.add("r.access_method");
                } else if ("contenttype".equals(value)) {
                    out.add("r.content_type");
                } else if ("format".equals(value)) {
                    out.add("r.format");
                } else if ("locator".equals(value)) {
                    out.add("r.locator");
                }
            }
        }
        if (out.isEmpty()) {
            out.add("m.name");
            out.add("m.description");
            out.add("m.application_scenario");
            out.add("m.identifier");
            out.add("m.owner");
            out.add("m.industry");
            out.add("m.data_category");
            out.add("m.registration_type");
            out.add("m.entity_type");
            out.add("r.component_type");
            out.add("r.storage_type");
            out.add("r.access_method");
            out.add("r.content_type");
            out.add("r.format");
            out.add("r.locator");
        }
        return out;
    }

    private double scoreFuzzy(
            List<String> tokens,
            String name,
            String desc,
            String app,
            String identifier,
            String owner,
            String industry,
            String dataCategory,
            String registrationType,
            String entityType,
            String componentType,
            String storageType,
            String accessMethod,
            String contentType,
            String format,
            String locator
    ) {
        if (tokens == null || tokens.isEmpty()) {
            return 0.0;
        }
        String n = lower(name);
        String d = lower(desc);
        String a = lower(app);
        String id = lower(identifier);
        String o = lower(owner);
        String ind = lower(industry);
        String cat = lower(dataCategory);
        String reg = lower(registrationType);
        String et = lower(entityType);
        String ct = lower(componentType);
        String st = lower(storageType);
        String am = lower(accessMethod);
        String ctt = lower(contentType);
        String f = lower(format);
        String loc = lower(locator);
        double raw = 0.0;
        for (String token : tokens) {
            if (token == null) {
                continue;
            }
            String value = token.toLowerCase(Locale.ROOT);
            double tokenScore = 0.0;
            if (!n.isEmpty() && n.contains(value)) {
                tokenScore = Math.max(tokenScore, 3.0);
            }
            if (!d.isEmpty() && d.contains(value)) {
                tokenScore = Math.max(tokenScore, 2.0);
            }
            if (!a.isEmpty() && a.contains(value)) {
                tokenScore = Math.max(tokenScore, 1.0);
            }
            if (!id.isEmpty() && id.contains(value)) {
                tokenScore = Math.max(tokenScore, id.startsWith(value) ? 2.0 : 1.5);
            }
            if (!o.isEmpty() && o.contains(value)) {
                tokenScore = Math.max(tokenScore, 1.2);
            }
            if (!ind.isEmpty() && ind.contains(value)) {
                tokenScore = Math.max(tokenScore, 1.2);
            }
            if (!cat.isEmpty() && cat.contains(value)) {
                tokenScore = Math.max(tokenScore, 1.2);
            }
            if (!reg.isEmpty() && reg.contains(value)) {
                tokenScore = Math.max(tokenScore, 1.2);
            }
            if (!et.isEmpty() && et.contains(value)) {
                tokenScore = Math.max(tokenScore, 1.0);
            }
            if (!ct.isEmpty() && ct.contains(value)) {
                tokenScore = Math.max(tokenScore, 1.3);
            }
            if (!st.isEmpty() && st.contains(value)) {
                tokenScore = Math.max(tokenScore, 1.0);
            }
            if (!am.isEmpty() && am.contains(value)) {
                tokenScore = Math.max(tokenScore, 1.0);
            }
            if (!ctt.isEmpty() && ctt.contains(value)) {
                tokenScore = Math.max(tokenScore, 1.0);
            }
            if (!f.isEmpty() && f.contains(value)) {
                tokenScore = Math.max(tokenScore, 1.0);
            }
            if (!loc.isEmpty() && loc.contains(value)) {
                tokenScore = Math.max(tokenScore, 0.8);
            }
            raw += tokenScore;
        }
        double maxPossible = tokens.size() * 3.0;
        return maxPossible == 0 ? 0.0 : raw / maxPossible;
    }

    private String lower(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT);
    }

    private static final class SearchPrefetchOrder {
        private final String expression;
        private final List<String> params;

        private SearchPrefetchOrder(String expression, List<String> params) {
            this.expression = expression;
            this.params = params == null ? Collections.<String>emptyList() : params;
        }
    }
}