package com.dris.sdk.next.enumtype;

import java.util.Locale;

public enum MetadataStatus {
    ACTIVE("active"),
    INACTIVE("inactive"),
    DELETED("deleted");

    private final String wireValue;

    MetadataStatus(String wireValue) {
        this.wireValue = wireValue;
    }

    public String wireValue() {
        return wireValue;
    }

    public static MetadataStatus fromValue(String value) {
        String normalized = normalize(value);
        for (MetadataStatus status : values()) {
            if (normalize(status.wireValue).equals(normalized) || normalize(status.name()).equals(normalized)) {
                return status;
            }
        }
        return null;
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }
}