package com.dris.sdk.next.enumtype;

import java.util.Locale;

public enum DataCategory {
    PUBLIC("1", "\u516c\u5171\u6570\u636e", "public"),
    ENTERPRISE("2", "\u4f01\u4e1a\u6570\u636e", "enterprise"),
    INDIVIDUAL("3", "\u4e2a\u4eba\u6570\u636e", "individual"),
    OTHERS("9", "\u5176\u4ed6", "others");

    private final String code;
    private final String displayText;
    private final String wireValue;

    DataCategory(String code, String displayText, String wireValue) {
        this.code = code;
        this.displayText = displayText;
        this.wireValue = wireValue;
    }

    public String getCode() { return code; }
    public String getDisplayText() { return displayText; }
    public String wireValue() { return wireValue; }

    public static DataCategory fromValue(String value) {
        String normalized = normalize(value);
        for (DataCategory category : values()) {
            if (normalize(category.code).equals(normalized)
                    || normalize(category.displayText).equals(normalized)
                    || normalize(category.wireValue).equals(normalized)
                    || normalize(category.name()).equals(normalized)) {
                return category;
            }
        }
        return null;
    }

    private static String normalize(String value) {
        if (value == null) {
            return "";
        }
        return value.trim().toLowerCase(Locale.ROOT);
    }
}