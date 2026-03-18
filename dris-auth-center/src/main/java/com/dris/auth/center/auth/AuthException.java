package com.dris.auth.center.auth;

import org.springframework.http.HttpStatus;

public class AuthException extends RuntimeException {
    private final String code;
    private final HttpStatus status;

    public AuthException(String code, String message, HttpStatus status) {
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
