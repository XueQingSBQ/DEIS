package com.dris.v2.backend.dto;

import java.util.ArrayList;
import java.util.List;

public class BatchResolveResourceResponse {
    private int total;
    private int successCount;
    private int failureCount;
    private boolean partial;
    private String mode;
    private List<BatchResolveResourceItem> items = new ArrayList<BatchResolveResourceItem>();

    public int getTotal() {
        return total;
    }

    public void setTotal(int total) {
        this.total = total;
    }

    public int getSuccessCount() {
        return successCount;
    }

    public void setSuccessCount(int successCount) {
        this.successCount = successCount;
    }

    public int getFailureCount() {
        return failureCount;
    }

    public void setFailureCount(int failureCount) {
        this.failureCount = failureCount;
    }

    public boolean isPartial() {
        return partial;
    }

    public void setPartial(boolean partial) {
        this.partial = partial;
    }

    public String getMode() {
        return mode;
    }

    public void setMode(String mode) {
        this.mode = mode;
    }

    public List<BatchResolveResourceItem> getItems() {
        return items;
    }

    public void setItems(List<BatchResolveResourceItem> items) {
        this.items = items == null ? new ArrayList<BatchResolveResourceItem>() : items;
    }
}
