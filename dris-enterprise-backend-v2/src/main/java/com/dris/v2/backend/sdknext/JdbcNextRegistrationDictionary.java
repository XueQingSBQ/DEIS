package com.dris.v2.backend.sdknext;

import com.dris.v2.backend.config.BackendV2Properties;
import com.dris.sdk.next.repository.RegistrationDictionary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class JdbcNextRegistrationDictionary implements RegistrationDictionary {
    private final JdbcTemplate jdbcTemplate;
    private final BackendV2Properties properties;

    public JdbcNextRegistrationDictionary(JdbcTemplate jdbcTemplate, BackendV2Properties properties) {
        this.jdbcTemplate = jdbcTemplate;
        this.properties = properties;
    }

    @Override
    public boolean existsRegionCode(String regionCode) {
        return existsByQuery("SELECT 1 FROM regions WHERE region_code=? LIMIT 1", regionCode);
    }

    @Override
    public boolean existsOrganizationCode(String organizationCode) {
        return safe(properties.getOrganizationCode()).equals(safe(organizationCode));
    }

    @Override
    public boolean existsIndustry(String industry) {
        return existsByQuery("SELECT 1 FROM industries WHERE industry=? LIMIT 1", industry);
    }

    @Override
    public boolean existsIndustryCode(String industryCode) {
        return existsByQuery("SELECT 1 FROM industries WHERE industry_code=? LIMIT 1", industryCode);
    }

    @Override
    public String resolveIndustryCode(String industry) {
        List<String> rows = jdbcTemplate.query(
                "SELECT industry_code FROM industries WHERE industry=? LIMIT 1",
                new Object[]{industry},
                (rs, rowNum) -> rs.getString(1)
        );
        return rows.isEmpty() ? null : rows.get(0);
    }

    private boolean existsByQuery(String sql, String arg) {
        List<Integer> rows = jdbcTemplate.query(
                sql,
                new Object[]{arg},
                (rs, rowNum) -> Integer.valueOf(1)
        );
        return !rows.isEmpty();
    }

    private String safe(String value) {
        return value == null ? "" : value.trim();
    }
}
