package com.dris.v2.backend.sdknext;

import com.dris.v2.backend.contract.DrisResponse;
import com.dris.v2.backend.model.BusinessException;
import com.dris.sdk.next.exception.ConflictException;
import com.dris.sdk.next.exception.DrisKernelException;
import com.dris.sdk.next.exception.NotFoundException;
import com.dris.sdk.next.exception.ValidationException;

public final class BackendKernelResponseSupport {
    private BackendKernelResponseSupport() {
    }

    public static <T> DrisResponse<T> success(T data) {
        return DrisResponse.success(data);
    }

    public static DrisResponse<Boolean> successBoolean() {
        return DrisResponse.success(Boolean.TRUE);
    }

    public static <T> DrisResponse<T> failure(Throwable throwable) {
        if (throwable instanceof BusinessException) {
            BusinessException exception = (BusinessException) throwable;
            return DrisResponse.failure(exception.getCode(), safeMessage(exception), null);
        }
        if (throwable instanceof ValidationException) {
            return DrisResponse.failure("VALIDATION_ERROR", safeMessage(throwable), null);
        }
        if (throwable instanceof NotFoundException) {
            return DrisResponse.failure("NOT_FOUND", safeMessage(throwable), null);
        }
        if (throwable instanceof ConflictException) {
            return DrisResponse.failure("CONFLICT", safeMessage(throwable), null);
        }
        if (throwable instanceof DrisKernelException) {
            DrisKernelException exception = (DrisKernelException) throwable;
            return DrisResponse.failure(exception.getErrorCode().name(), safeMessage(exception), null);
        }
        return DrisResponse.failure("INTERNAL_ERROR", safeMessage(throwable), null);
    }

    private static String safeMessage(Throwable throwable) {
        if (throwable == null || throwable.getMessage() == null || throwable.getMessage().trim().isEmpty()) {
            return "internal error";
        }
        return throwable.getMessage().trim();
    }
}
