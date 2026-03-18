package com.dris.sdk.next.enumtype;

import java.util.Locale;

public enum RegistrationType {
    RESOURCE("1", "\u6570\u636e\u8d44\u6e90", "resource"),
    ASSET("2", "\u6570\u636e\u8d44\u4ea7", "asset"),
    PRODUCT_SERVICE("3", "\u6570\u636e\u4ea7\u54c1/\u670d\u52a1", "product_service");

    private final String code;
    private final String displayText;
    private final String wireValue;

    RegistrationType(String code, String displayText, String wireValue) {
        this.code = code;
        this.displayText = displayText;
        this.wireValue = wireValue;
    }

    public String getCode() { return code; }
    public String getDisplayText() { return displayText; }
    public String wireValue() { return wireValue; }

    public static RegistrationType fromValue(String value) {
        String normalized = normalize(value);
        for (RegistrationType type : values()) {
            if (normalize(type.code).equals(normalized)
                    || normalize(type.displayText).equals(normalized)
                    || normalize(type.wireValue).equals(normalized)
                    || normalize(type.name()).equals(normalized)) {
                return type;
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