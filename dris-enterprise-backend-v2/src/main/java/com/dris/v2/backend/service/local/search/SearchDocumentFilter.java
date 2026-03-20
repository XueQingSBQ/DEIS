package com.dris.v2.backend.service.local.search;

import com.dris.v2.backend.dao.SearchDocumentRow;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class SearchDocumentFilter {
    public boolean matches(SearchDocumentRow row, Map<String, String> filters) {
        if (row == null) {
            return false;
        }
        if (filters == null || filters.isEmpty()) {
            return true;
        }
        for (Map.Entry<String, String> entry : filters.entrySet()) {
            String key = entry.getKey();
            String value = entry.getValue();
            if ("industry".equals(key) && !eqIgnoreCase(row.getIndustry(), value)) {
                return false;
            }
            if ("status".equals(key) && !eqIgnoreCase(row.getStatus(), value)) {
                return false;
            }
            if ("entityType".equals(key) && !eqIgnoreCase(row.getEntityType(), value)) {
                return false;
            }
            if ("dataCategory".equals(key) && !eqIgnoreCase(row.getDataCategory(), value)) {
                return false;
            }
            if ("owner".equals(key) && !eqIgnoreCase(row.getOwner(), value)) {
                return false;
            }
            if ("registrationType".equals(key) && !eqIgnoreCase(row.getRegistrationType(), value)) {
                return false;
            }
            if ("componentType".equals(key) && !eqIgnoreCase(row.getComponentType(), value)) {
                return false;
            }
            if ("storageType".equals(key) && !eqIgnoreCase(row.getStorageType(), value)) {
                return false;
            }
            if ("accessMethod".equals(key) && !eqIgnoreCase(row.getAccessMethod(), value)) {
                return false;
            }
            if ("contentType".equals(key) && !eqIgnoreCase(row.getContentType(), value)) {
                return false;
            }
            if ("format".equals(key) && !eqIgnoreCase(row.getFormat(), value)) {
                return false;
            }
        }
        return true;
    }

    private boolean eqIgnoreCase(String left, String right) {
        return SearchTextSupport.trim(left).equalsIgnoreCase(SearchTextSupport.trim(right));
    }
}
