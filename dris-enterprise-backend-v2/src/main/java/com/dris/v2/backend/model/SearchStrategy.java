package com.dris.v2.backend.model;

public enum SearchStrategy {
    RECURSIVE,
    ITERATIVE;

    public static SearchStrategy parse(String value) {
        if (value == null || value.trim().isEmpty()) {
            return RECURSIVE;
        }
        return "iterative".equalsIgnoreCase(value.trim()) ? ITERATIVE : RECURSIVE;
    }
}
