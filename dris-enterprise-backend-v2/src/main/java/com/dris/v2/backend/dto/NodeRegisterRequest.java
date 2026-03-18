package com.dris.v2.backend.dto;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Pattern;

public class NodeRegisterRequest {
    @NotBlank
    private String childNodeName;
    @NotBlank
    @Pattern(regexp = "^[0-9]{4}$", message = "regionCode must be 4 digits")
    private String regionCode;
    @NotBlank
    @Pattern(regexp = "^[A-HJ-NP-Z]{4}$", message = "orgCode must be 4 uppercase letters excluding I and O")
    private String orgCode;
    @NotBlank
    private String childDrirpUrl;

    public String getChildNodeName() {
        return childNodeName;
    }

    public void setChildNodeName(String childNodeName) {
        this.childNodeName = childNodeName;
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

    public String getChildDrirpUrl() {
        return childDrirpUrl;
    }

    public void setChildDrirpUrl(String childDrirpUrl) {
        this.childDrirpUrl = childDrirpUrl;
    }
}


