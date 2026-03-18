package com.dris.v2.backend.model;

import java.time.Instant;

public class RouteForwardRecord {
    private String targetCode;
    private String targetRegionCode;
    private String targetOrgCode;
    private String targetNodeName;
    private String targetLevel;
    private String nextHopCode;
    private String nextHopDrirpUrl;
    private int hopCount;
    private String routeType;
    private long routeVersion;
    private String sourceNodeName;
    private Instant updatedAt;

    public String getTargetCode() {
        return targetCode;
    }

    public void setTargetCode(String targetCode) {
        this.targetCode = targetCode;
    }

    public String getTargetRegionCode() {
        return targetRegionCode;
    }

    public void setTargetRegionCode(String targetRegionCode) {
        this.targetRegionCode = targetRegionCode;
    }

    public String getTargetOrgCode() {
        return targetOrgCode;
    }

    public void setTargetOrgCode(String targetOrgCode) {
        this.targetOrgCode = targetOrgCode;
    }

    public String getTargetNodeName() {
        return targetNodeName;
    }

    public void setTargetNodeName(String targetNodeName) {
        this.targetNodeName = targetNodeName;
    }

    public String getTargetLevel() {
        return targetLevel;
    }

    public void setTargetLevel(String targetLevel) {
        this.targetLevel = targetLevel;
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

    public int getHopCount() {
        return hopCount;
    }

    public void setHopCount(int hopCount) {
        this.hopCount = hopCount;
    }

    public String getRouteType() {
        return routeType;
    }

    public void setRouteType(String routeType) {
        this.routeType = routeType;
    }

    public long getRouteVersion() {
        return routeVersion;
    }

    public void setRouteVersion(long routeVersion) {
        this.routeVersion = routeVersion;
    }

    public String getSourceNodeName() {
        return sourceNodeName;
    }

    public void setSourceNodeName(String sourceNodeName) {
        this.sourceNodeName = sourceNodeName;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}
