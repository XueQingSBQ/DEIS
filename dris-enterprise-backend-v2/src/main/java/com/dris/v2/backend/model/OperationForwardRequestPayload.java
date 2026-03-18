package com.dris.v2.backend.model;

import com.dris.v2.backend.dto.ToolExecuteRequest;
import com.dris.v2.backend.contract.upload.DownloadPresignRequest;

import java.util.ArrayList;
import java.util.List;

public class OperationForwardRequestPayload {
    private String identifier;
    private String operationType;
    private Integer ttl;
    private String userId;
    private List<String> visitedUrls = new ArrayList<String>();
    private ToolExecuteRequest toolExecuteRequest;
    private DownloadPresignRequest downloadPresignRequest;

    public String getIdentifier() {
        return identifier;
    }

    public void setIdentifier(String identifier) {
        this.identifier = identifier;
    }

    public String getOperationType() {
        return operationType;
    }

    public void setOperationType(String operationType) {
        this.operationType = operationType;
    }

    public Integer getTtl() {
        return ttl;
    }

    public void setTtl(Integer ttl) {
        this.ttl = ttl;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public List<String> getVisitedUrls() {
        return visitedUrls;
    }

    public void setVisitedUrls(List<String> visitedUrls) {
        this.visitedUrls = visitedUrls == null ? new ArrayList<String>() : visitedUrls;
    }

    public ToolExecuteRequest getToolExecuteRequest() {
        return toolExecuteRequest;
    }

    public void setToolExecuteRequest(ToolExecuteRequest toolExecuteRequest) {
        this.toolExecuteRequest = toolExecuteRequest;
    }

    public DownloadPresignRequest getDownloadPresignRequest() {
        return downloadPresignRequest;
    }

    public void setDownloadPresignRequest(DownloadPresignRequest downloadPresignRequest) {
        this.downloadPresignRequest = downloadPresignRequest;
    }
}
