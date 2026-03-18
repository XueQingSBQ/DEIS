package com.dris.v2.backend.dto;

public class DirectDescendantView {
    private String childCode;
    private String childNodeName;
    private String childRegionCode;
    private String childOrgCode;
    private String childLevel;
    private String childDrirpUrl;
    private String relationStatus;
    private String healthStatus;
    private String firstSeenAt;
    private String lastSeenAt;

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

    public String getChildRegionCode() {
        return childRegionCode;
    }

    public void setChildRegionCode(String childRegionCode) {
        this.childRegionCode = childRegionCode;
    }

    public String getChildOrgCode() {
        return childOrgCode;
    }

    public void setChildOrgCode(String childOrgCode) {
        this.childOrgCode = childOrgCode;
    }

    public String getChildLevel() {
        return childLevel;
    }

    public void setChildLevel(String childLevel) {
        this.childLevel = childLevel;
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

    public String getFirstSeenAt() {
        return firstSeenAt;
    }

    public void setFirstSeenAt(String firstSeenAt) {
        this.firstSeenAt = firstSeenAt;
    }

    public String getLastSeenAt() {
        return lastSeenAt;
    }

    public void setLastSeenAt(String lastSeenAt) {
        this.lastSeenAt = lastSeenAt;
    }
}
