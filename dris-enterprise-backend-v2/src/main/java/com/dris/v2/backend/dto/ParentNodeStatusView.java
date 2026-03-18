package com.dris.v2.backend.dto;

public class ParentNodeStatusView {
    private String parentCode;
    private String parentNodeName;
    private String parentDrirpUrl;
    private String relationStatus;
    private String healthStatus;
    private String lastCheckedAt;
    private String lastSuccessAt;
    private String lastMessage;
    private boolean observed;
    private boolean online;

    public String getParentCode() {
        return parentCode;
    }

    public void setParentCode(String parentCode) {
        this.parentCode = parentCode;
    }

    public String getParentNodeName() {
        return parentNodeName;
    }

    public void setParentNodeName(String parentNodeName) {
        this.parentNodeName = parentNodeName;
    }

    public String getParentDrirpUrl() {
        return parentDrirpUrl;
    }

    public void setParentDrirpUrl(String parentDrirpUrl) {
        this.parentDrirpUrl = parentDrirpUrl;
    }

    public String getRelationStatus() {
        return relationStatus;
    }

    public void setRelationStatus(String relationStatus) {
        this.relationStatus = relationStatus;
    }

    public String getHealthStatus() {
        return healthStatus;
    }

    public void setHealthStatus(String healthStatus) {
        this.healthStatus = healthStatus;
    }

    public String getLastCheckedAt() {
        return lastCheckedAt;
    }

    public void setLastCheckedAt(String lastCheckedAt) {
        this.lastCheckedAt = lastCheckedAt;
    }

    public String getLastSuccessAt() {
        return lastSuccessAt;
    }

    public void setLastSuccessAt(String lastSuccessAt) {
        this.lastSuccessAt = lastSuccessAt;
    }

    public String getLastMessage() {
        return lastMessage;
    }

    public void setLastMessage(String lastMessage) {
        this.lastMessage = lastMessage;
    }

    public boolean isObserved() {
        return observed;
    }

    public void setObserved(boolean observed) {
        this.observed = observed;
    }

    public boolean isOnline() {
        return online;
    }

    public void setOnline(boolean online) {
        this.online = online;
    }
}
