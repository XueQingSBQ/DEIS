package com.dris.v2.backend.dto;

public class NodeBootstrapConfig {
    private String nodeName;
    private String nodeLevel;
    private String regionCode;
    private String orgCode;
    private String parentNodeName;
    private String parentDrirpUrl;
    private boolean drirpEnabled;
    private boolean startupHelloEnabled;

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

    public boolean isDrirpEnabled() {
        return drirpEnabled;
    }

    public void setDrirpEnabled(boolean drirpEnabled) {
        this.drirpEnabled = drirpEnabled;
    }

    public boolean isStartupHelloEnabled() {
        return startupHelloEnabled;
    }

    public void setStartupHelloEnabled(boolean startupHelloEnabled) {
        this.startupHelloEnabled = startupHelloEnabled;
    }
}
