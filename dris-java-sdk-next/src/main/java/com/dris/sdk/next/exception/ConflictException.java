package com.dris.sdk.next.exception;

public class ConflictException extends DrisKernelException {
    public ConflictException(String message) {
        super(DrisKernelErrorCode.CONFLICT, message);
    }
}