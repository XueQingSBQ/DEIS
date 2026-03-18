package com.dris.sdk.next.exception;

public class NotFoundException extends DrisKernelException {
    public NotFoundException(String message) {
        super(DrisKernelErrorCode.NOT_FOUND, message);
    }
}