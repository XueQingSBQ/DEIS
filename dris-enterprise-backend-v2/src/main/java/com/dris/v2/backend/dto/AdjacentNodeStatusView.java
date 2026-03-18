package com.dris.v2.backend.dto;

public class AdjacentNodeStatusView {
    private String nodeCode;
    private String nodeName;
    private String nodeLevel;
    private String drirpUrl;
    private String direction;
    private String relationStatus;
    private String healthStatus;
    private String lastCheckedAt;
    private String lastSeenAt;
    private String message;
    private long staleMs;
    private boolean observed;
    private boolean online;

    public String getNodeCode() {
        return nodeCode;
    }

    public void setNodeCode(String nodeCode) {
        this.nodeCode = nodeCode;
    }

    public String getNodeName() {
        return nodeName;
    }

    public void setNodeName(String nodeName) {
        this.nodeName = nodeName;
    }

    public String getNodeLevel() {
        return nodeLevel;
    }

    public void setNodeLevel(String nodeLevel) {
        this.nodeLevel = nodeLevel;
    }

    public String getDrirpUrl() {
        return drirpUrl;
    }

    public void setDrirpUrl(String drirpUrl) {
        this.drirpUrl = drirpUrl;
    }

    public String getDirection() {
        return direction;
    }

    public void setDirection(String direction) {
        this.direction = direction;
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

    public String getLastSeenAt() {
        return lastSeenAt;
    }

    public void setLastSeenAt(String lastSeenAt) {
        this.lastSeenAt = lastSeenAt;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public long getStaleMs() {
        return staleMs;
    }

    public void setStaleMs(long staleMs) {
        this.staleMs = staleMs;
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
