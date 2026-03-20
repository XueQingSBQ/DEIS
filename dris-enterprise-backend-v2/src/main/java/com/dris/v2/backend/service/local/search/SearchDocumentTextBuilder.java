package com.dris.v2.backend.service.local.search;

import com.dris.v2.backend.dao.SearchDocumentRow;
import org.springframework.stereotype.Component;

@Component
public class SearchDocumentTextBuilder {
    public String build(SearchDocumentRow row) {
        if (row == null) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        append(builder, row.getName());
        append(builder, row.getDescription());
        append(builder, row.getApplicationScenario());
        append(builder, row.getOwner());
        append(builder, row.getIndustry());
        append(builder, row.getDataCategory());
        append(builder, row.getRegistrationType());
        append(builder, row.getEntityType());
        append(builder, row.getComponentType());
        append(builder, row.getStorageType());
        append(builder, row.getAccessMethod());
        append(builder, row.getContentType());
        append(builder, row.getFormat());
        append(builder, row.getAdditionalAttributesText());
        return builder.toString();
    }

    private void append(StringBuilder builder, String value) {
        String trimmed = SearchTextSupport.trim(value);
        if (trimmed.isEmpty()) {
            return;
        }
        if (builder.length() > 0) {
            builder.append(' ');
        }
        builder.append(trimmed);
    }
}
