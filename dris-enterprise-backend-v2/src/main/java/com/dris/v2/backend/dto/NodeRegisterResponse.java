package com.dris.v2.backend.dto;

public class NodeRegisterResponse {
    private String childNodeName;
    private String childLevel;
    private String regionCode;
    private String orgCode;
    private String relationStatus;
    private String healthStatus;
    private NodeBootstrapConfig bootstrapConfig;

    public String getChildNodeName() {
        return childNodeName;
    }

    public void setChildNodeName(String childNodeName) {
        this.childNodeName = childNodeName;
    }

    public String getChildLevel() {
        return childLevel;
    }

    public void setChildLevel(String childLevel) {
        this.childLevel = childLevel;
    }

    public String getRegionCode() {
        return regionCode;
    }

    public void setRegionCode(String regionCode) {
        this.regionCode = regionCode;
    }

    public String getOrgCode() {
        return orgCode;
    }

    public void setOrgCode(String orgCode) {
        this.orgCode = orgCode;
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

    public NodeBootstrapConfig getBootstrapConfig() {
        return bootstrapConfig;
    }

    public void setBootstrapConfig(NodeBootstrapConfig bootstrapConfig) {
        this.bootstrapConfig = bootstrapConfig;
    }
}
