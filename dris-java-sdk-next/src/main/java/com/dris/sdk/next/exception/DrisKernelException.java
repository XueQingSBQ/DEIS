package com.dris.sdk.next.exception;

public class DrisKernelException extends RuntimeException {
    private final DrisKernelErrorCode errorCode;

    public DrisKernelException(DrisKernelErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public DrisKernelException(DrisKernelErrorCode errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }

    public DrisKernelErrorCode getErrorCode() {
        return errorCode;
    }
}