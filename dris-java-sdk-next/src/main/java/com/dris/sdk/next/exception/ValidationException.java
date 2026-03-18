package com.dris.sdk.next.exception;

public class ValidationException extends DrisKernelException {
    public ValidationException(String message) {
        super(DrisKernelErrorCode.VALIDATION_ERROR, message);
    }
}