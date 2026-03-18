package com.dris.v2.backend.contract.upload;

import java.util.LinkedHashMap;
import java.util.Map;

public class PresignedPut {
    private String url;
    private Map<String, String> headers = new LinkedHashMap<>();
    private int expiresInSeconds;

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public Map<String, String> getHeaders() {
        return headers;
    }

    public void setHeaders(Map<String, String> headers) {
        this.headers = headers == null ? new LinkedHashMap<>() : headers;
    }

    public int getExpiresInSeconds() {
        return expiresInSeconds;
    }

    public void setExpiresInSeconds(int expiresInSeconds) {
        this.expiresInSeconds = expiresInSeconds;
    }
}

