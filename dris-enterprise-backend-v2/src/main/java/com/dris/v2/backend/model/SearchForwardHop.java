package com.dris.v2.backend.model;

public class SearchForwardHop {
    private String nodeCode;
    private String drirpUrl;

    public SearchForwardHop() {
    }

    public SearchForwardHop(String nodeCode, String drirpUrl) {
        this.nodeCode = nodeCode;
        this.drirpUrl = drirpUrl;
    }

    public String getNodeCode() {
        return nodeCode;
    }

    public void setNodeCode(String nodeCode) {
        this.nodeCode = nodeCode;
    }

    public String getDrirpUrl() {
        return drirpUrl;
    }

    public void setDrirpUrl(String drirpUrl) {
        this.drirpUrl = drirpUrl;
    }
}
