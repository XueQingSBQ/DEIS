package com.dris.v2.backend.vector;

import com.dris.sdk.next.domain.IdentifierMetadata;

import java.util.LinkedHashMap;
import java.util.Map;

public class VectorMetadataDocumentBuilder {
    public String buildText(IdentifierMetadata metadata) {
        if (metadata == null) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        append(builder, "identifier", metadata.getIdentifier());
        append(builder, "name", metadata.getName());
        append(builder, "description", metadata.getDescription());
        append(builder, "applicationScenario", metadata.getApplicationScenario());
        append(builder, "owner", metadata.getOwner());
        append(builder, "industry", metadata.getIndustry());
        append(builder, "dataCategory", metadata.getDataCategory());
        append(builder, "registrationType", metadata.getRegistrationType());
        append(builder, "entityType", metadata.getEntityType());
        append(builder, "regionCode", metadata.getRegionCode());
        append(builder, "organizationCode", metadata.getOrganizationCode());
        append(builder, "status", metadata.getStatus());
        appendAdditional(builder, metadata.getAdditionalAttributes());
        return builder.toString();
    }

    public Map<String, Object> buildMetadata(IdentifierMetadata metadata) {
        LinkedHashMap<String, Object> values = new LinkedHashMap<String, Object>();
        if (metadata == null) {
            return values;
        }
        put(values, "identifier", metadata.getIdentifier());
        put(values, "name", metadata.getName());
        put(values, "description", metadata.getDescription());
        put(values, "applicationScenario", metadata.getApplicationScenario());
        put(values, "owner", metadata.getOwner());
        put(values, "industry", metadata.getIndustry());
        put(values, "dataCategory", metadata.getDataCategory());
        put(values, "registrationType", metadata.getRegistrationType());
        put(values, "entityType", metadata.getEntityType());
        put(values, "regionCode", metadata.getRegionCode());
        put(values, "organizationCode", metadata.getOrganizationCode());
        put(values, "status", metadata.getStatus());
        put(values, "createTime", metadata.getCreateTime());
        put(values, "updateTime", metadata.getUpdateTime());
        putAdditional(values, metadata.getAdditionalAttributes());
        return values;
    }

    private void append(StringBuilder builder, String label, String value) {
        String normalized = normalizeText(value);
        if (normalized.isEmpty()) {
            return;
        }
        if (builder.length() > 0) {
            builder.append('\n');
        }
        builder.append(label).append(": ").append(normalized);
    }

    private void appendAdditional(StringBuilder builder, Map<String, Object> attributes) {
        if (attributes == null || attributes.isEmpty()) {
            return;
        }
        for (Map.Entry<String, Object> entry : attributes.entrySet()) {
            if (entry == null) {
                continue;
            }
            String key = normalizeText(entry.getKey());
            String value = normalizeText(entry.getValue());
            if (key.isEmpty() || value.isEmpty()) {
                continue;
            }
            append(builder, key, value);
        }
    }

    private void put(Map<String, Object> values, String key, String value) {
        String normalized = normalizeText(value);
        if (!normalized.isEmpty()) {
            values.put(key, normalized);
        }
    }

    private void putAdditional(Map<String, Object> values, Map<String, Object> attributes) {
        if (attributes == null || attributes.isEmpty()) {
            return;
        }
        for (Map.Entry<String, Object> entry : attributes.entrySet()) {
            if (entry == null) {
                continue;
            }
            String key = normalizeText(entry.getKey());
            Object value = normalizeMetadataValue(entry.getValue());
            if (key.isEmpty() || value == null) {
                continue;
            }
            values.put("attr." + key, value);
        }
    }

    private String normalizeText(Object value) {
        if (value == null) {
            return "";
        }
        return String.valueOf(value).trim();
    }

    private Object normalizeMetadataValue(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number || value instanceof Boolean) {
            return value;
        }
        String text = normalizeText(value);
        return text.isEmpty() ? null : text;
    }
}
