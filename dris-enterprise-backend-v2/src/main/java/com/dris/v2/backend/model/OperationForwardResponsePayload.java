package com.dris.v2.backend.model;

import com.dris.v2.backend.dto.ToolExecuteResult;
import com.dris.v2.backend.contract.upload.DownloadPresignResult;

import java.util.ArrayList;
import java.util.List;

public class OperationForwardResponsePayload {
    private Boolean delegate = Boolean.FALSE;
    private String operationType;
    private String nextHopCode;
    private String nextHopDrirpUrl;
    private Integer ttlRemaining;
    private List<String> visitedUrls = new ArrayList<String>();
    private String handledBy;
    private ToolExecuteResult toolExecuteResult;
    private DownloadPresignResult downloadPresignResult;

    public Boolean getDelegate() {
        return delegate;
    }

    public void setDelegate(Boolean delegate) {
        this.delegate = delegate;
    }

    public String getOperationType() {
        return operationType;
    }

    public void setOperationType(String operationType) {
        this.operationType = operationType;
    }

    public String getNextHopCode() {
        return nextHopCode;
    }

    public void setNextHopCode(String nextHopCode) {
        this.nextHopCode = nextHopCode;
    }

    public String getNextHopDrirpUrl() {
        return nextHopDrirpUrl;
    }

    public void setNextHopDrirpUrl(String nextHopDrirpUrl) {
        this.nextHopDrirpUrl = nextHopDrirpUrl;
    }

    public Integer getTtlRemaining() {
        return ttlRemaining;
    }

    public void setTtlRemaining(Integer ttlRemaining) {
        this.ttlRemaining = ttlRemaining;
    }

    public List<String> getVisitedUrls() {
        return visitedUrls;
    }

    public void setVisitedUrls(List<String> visitedUrls) {
        this.visitedUrls = visitedUrls == null ? new ArrayList<String>() : visitedUrls;
    }

    public String getHandledBy() {
        return handledBy;
    }

    public void setHandledBy(String handledBy) {
        this.handledBy = handledBy;
    }

    public ToolExecuteResult getToolExecuteResult() {
        return toolExecuteResult;
    }

    public void setToolExecuteResult(ToolExecuteResult toolExecuteResult) {
        this.toolExecuteResult = toolExecuteResult;
    }

    public DownloadPresignResult getDownloadPresignResult() {
        return downloadPresignResult;
    }

    public void setDownloadPresignResult(DownloadPresignResult downloadPresignResult) {
        this.downloadPresignResult = downloadPresignResult;
    }
}
