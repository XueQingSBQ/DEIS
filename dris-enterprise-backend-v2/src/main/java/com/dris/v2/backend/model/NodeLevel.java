package com.dris.v2.backend.model;

public enum NodeLevel {
    COUNTRY,
    PROVINCE,
    CITY,
    COUNTY;

    public static NodeLevel parse(String value) {
        if (value == null || value.trim().isEmpty()) {
            return COUNTRY;
        }
        return NodeLevel.valueOf(value.trim().toUpperCase());
    }

    public NodeLevel nextLevel() {
        switch (this) {
            case COUNTRY:
                return PROVINCE;
            case PROVINCE:
                return CITY;
            case CITY:
                return COUNTY;
            default:
                return null;
        }
    }
}
