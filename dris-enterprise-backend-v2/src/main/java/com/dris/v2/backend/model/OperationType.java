package com.dris.v2.backend.model;

public enum OperationType {
    TOOL_EXECUTE,
    DOWNLOAD_PRESIGN;

    public static OperationType parse(String value) {
        if (value == null || value.trim().isEmpty()) {
            return TOOL_EXECUTE;
        }
        if ("TOOL_EXECUTE".equalsIgnoreCase(value.trim())) {
            return TOOL_EXECUTE;
        }
        if ("DOWNLOAD_PRESIGN".equalsIgnoreCase(value.trim())) {
            return DOWNLOAD_PRESIGN;
        }
        return null;
    }
}
