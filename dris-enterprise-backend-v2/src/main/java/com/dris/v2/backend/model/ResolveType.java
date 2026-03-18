package com.dris.v2.backend.model;

public enum ResolveType {
    METADATA,
    DIGITAL_OBJECT,
    RESOURCE;

    public static ResolveType parse(String value) {
        if (value == null || value.trim().isEmpty()) {
            return METADATA;
        }
        if ("DIGITAL_OBJECT".equalsIgnoreCase(value.trim())) {
            return DIGITAL_OBJECT;
        }
        if ("RESOURCE".equalsIgnoreCase(value.trim())) {
            return RESOURCE;
        }
        return METADATA;
    }
}
