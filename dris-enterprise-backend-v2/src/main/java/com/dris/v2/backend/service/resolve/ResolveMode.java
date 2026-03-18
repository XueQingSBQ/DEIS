package com.dris.v2.backend.service.resolve;

public enum ResolveMode {
    RECURSIVE,
    ITERATIVE;

    public static ResolveMode parse(String value) {
        if (value == null || value.trim().isEmpty()) {
            return RECURSIVE;
        }
        return "iterative".equalsIgnoreCase(value.trim()) ? ITERATIVE : RECURSIVE;
    }
}
