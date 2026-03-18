package com.dris.v2.backend.model;

import org.springframework.http.HttpStatus;

public class BusinessException extends RuntimeException {
    private final String code;
    private final HttpStatus status;

    public BusinessException(String code, String message, HttpStatus status) {
        super(message);
        this.code = code == null ? "ERROR" : code;
        this.status = status == null ? HttpStatus.BAD_REQUEST : status;
    }

    public String getCode() {
        return code;
    }

    public HttpStatus getStatus() {
        return status;
    }
}
