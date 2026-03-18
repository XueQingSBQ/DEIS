package com.dris.v2.backend.dto;

public class DirectNodeStatusView {
    private String childCode;
    private String childNodeName;
    private String childDrirpUrl;
    private String relationStatus;
    private String healthStatus;
    private String lastSeenAt;
    private boolean online;
    private long staleMs;

    public String getChildCode() {
        return childCode;
    }

    public void setChildCode(String childCode) {
        this.childCode = childCode;
    }

    public String getChildNodeName() {
        return childNodeName;
    }

    public void setChildNodeName(String childNodeName) {
        this.childNodeName = childNodeName;
    }

    public String getChildDrirpUrl() {
        return childDrirpUrl;
    }

    public void setChildDrirpUrl(String childDrirpUrl) {
        this.childDrirpUrl = childDrirpUrl;
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

    public String getLastSeenAt() {
        return lastSeenAt;
    }

    public void setLastSeenAt(String lastSeenAt) {
        this.lastSeenAt = lastSeenAt;
    }

    public boolean isOnline() {
        return online;
    }

    public void setOnline(boolean online) {
        this.online = online;
    }

    public long getStaleMs() {
        return staleMs;
    }

    public void setStaleMs(long staleMs) {
        this.staleMs = staleMs;
    }
}
