package com.dris.v2.backend.model;

public enum SearchForwardPhase {
    LOCATE_ANCHOR,
    SEARCH_SUBTREE;

    public static SearchForwardPhase parse(String value) {
        if (value == null || value.trim().isEmpty()) {
            return SEARCH_SUBTREE;
        }
        if ("LOCATE_ANCHOR".equalsIgnoreCase(value.trim())) {
            return LOCATE_ANCHOR;
        }
        return SEARCH_SUBTREE;
    }
}
