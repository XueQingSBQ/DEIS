package com.dris.sdk.next.application.support;

import com.dris.sdk.next.exception.DrisKernelErrorCode;
import com.dris.sdk.next.exception.DrisKernelException;
import com.dris.sdk.next.exception.NotFoundException;
import com.dris.sdk.next.result.BatchItemResult;
import com.dris.sdk.next.result.BatchItemStatus;
import com.dris.sdk.next.result.BatchResult;

import java.util.ArrayList;
import java.util.List;

public class BatchResultBuilder<T> {
    private final List<BatchItemResult<T>> items = new ArrayList<BatchItemResult<T>>();

    public void addSuccess(String key, T data) {
        items.add(BatchItemResult.success(key, data));
    }

    public void addFailure(String key, Throwable throwable) {
        items.add(BatchItemResult.failure(
                key,
                resolveStatus(throwable),
                resolveCode(throwable),
                throwable == null ? null : throwable.getMessage()
        ));
    }

    public BatchResult<T> build() {
        return BatchResult.of(items);
    }

    private BatchItemStatus resolveStatus(Throwable throwable) {
        if (throwable instanceof NotFoundException) {
            return BatchItemStatus.NOT_FOUND;
        }
        return BatchItemStatus.FAILED;
    }

    private String resolveCode(Throwable throwable) {
        if (throwable instanceof DrisKernelException) {
            return ((DrisKernelException) throwable).getErrorCode().name();
        }
        return DrisKernelErrorCode.INTERNAL_ERROR.name();
    }
}