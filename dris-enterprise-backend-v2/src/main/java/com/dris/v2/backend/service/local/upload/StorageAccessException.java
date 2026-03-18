package com.dris.v2.backend.service.local.upload;

public class StorageAccessException extends RuntimeException {
    private final String code;

    public StorageAccessException(String code, String message) {
        super(message == null ? "storage operation failed" : message);
        this.code = code == null || code.trim().isEmpty() ? "STORAGE_ERROR" : code.trim();
    }

    public String getCode() {
        return code;
    }
}
