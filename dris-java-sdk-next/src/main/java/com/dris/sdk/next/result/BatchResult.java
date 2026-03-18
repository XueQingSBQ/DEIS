package com.dris.sdk.next.result;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class BatchResult<T> {
    private final List<BatchItemResult<T>> items;
    private final int totalCount;
    private final int successCount;
    private final int failureCount;

    private BatchResult(List<BatchItemResult<T>> items, int totalCount, int successCount, int failureCount) {
        this.items = items;
        this.totalCount = totalCount;
        this.successCount = successCount;
        this.failureCount = failureCount;
    }

    public static <T> BatchResult<T> of(List<BatchItemResult<T>> items) {
        List<BatchItemResult<T>> safeItems = items == null
                ? Collections.<BatchItemResult<T>>emptyList()
                : Collections.unmodifiableList(new ArrayList<BatchItemResult<T>>(items));
        int success = 0;
        for (BatchItemResult<T> item : safeItems) {
            if (item.getStatus() == BatchItemStatus.SUCCESS) {
                success++;
            }
        }
        return new BatchResult<T>(safeItems, safeItems.size(), success, safeItems.size() - success);
    }

    public List<BatchItemResult<T>> getItems() {
        return items;
    }

    public int getTotalCount() {
        return totalCount;
    }

    public int getSuccessCount() {
        return successCount;
    }

    public int getFailureCount() {
        return failureCount;
    }

    public boolean isAllSucceeded() {
        return failureCount == 0;
    }
}