package com.dris.v2.backend.dto;

import java.util.ArrayList;
import java.util.List;

public class NodeShutdownNotifyResponse {
    private int totalTargets;
    private int successCount;
    private int failureCount;
    private String notifiedAt;
    private List<String> failedTargets = new ArrayList<String>();

    public int getTotalTargets() {
        return totalTargets;
    }

    public void setTotalTargets(int totalTargets) {
        this.totalTargets = totalTargets;
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

    public String getNotifiedAt() {
        return notifiedAt;
    }

    public void setNotifiedAt(String notifiedAt) {
        this.notifiedAt = notifiedAt;
    }

    public List<String> getFailedTargets() {
        return failedTargets;
    }

    public void setFailedTargets(List<String> failedTargets) {
        this.failedTargets = failedTargets == null ? new ArrayList<String>() : failedTargets;
    }
}
