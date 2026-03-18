package com.dris.sdk.next.enumtype;

public enum ComponentType {
    RESOURCE("resource"),
    TOOL("tool"),
    SERVICE("service");

    private final String wireValue;

    ComponentType(String wireValue) {
        this.wireValue = wireValue;
    }

    public String wireValue() {
        return wireValue;
    }

    public static ComponentType fromValue(String value) {
        String normalized = normalize(value);
        for (ComponentType type : values()) {
            if (normalize(type.wireValue).equals(normalized) || normalize(type.name()).equals(normalized)) {
                return type;
            }
        }
        return null;
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(java.util.Locale.ROOT);
    }
}