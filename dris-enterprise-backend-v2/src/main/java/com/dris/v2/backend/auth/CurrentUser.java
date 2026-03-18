package com.dris.v2.backend.auth;

public class CurrentUser {
    private final String userId;
    private final String username;
    private final String displayName;
    private final String orgCode;
    private final String tokenId;

    public CurrentUser(String userId, String username, String displayName, String orgCode, String tokenId) {
        this.userId = userId == null ? "" : userId;
        this.username = username == null ? "" : username;
        this.displayName = displayName == null ? "" : displayName;
        this.orgCode = orgCode == null ? "" : orgCode;
        this.tokenId = tokenId == null ? "" : tokenId;
    }

    public String getUserId() {
        return userId;
    }

    public String getUsername() {
        return username;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getOrgCode() {
        return orgCode;
    }

    public String getTokenId() {
        return tokenId;
    }
}
