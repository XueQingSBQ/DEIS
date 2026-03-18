package com.dris.v2.backend.contract.upload;

public class DownloadPresignResult {
    private String url;
    private int expiresInSeconds;
    private String filename;
    private String mime;
    private long size;
    private String etag;

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public int getExpiresInSeconds() {
        return expiresInSeconds;
    }

    public void setExpiresInSeconds(int expiresInSeconds) {
        this.expiresInSeconds = expiresInSeconds;
    }

    public String getFilename() {
        return filename;
    }

    public void setFilename(String filename) {
        this.filename = filename;
    }

    public String getMime() {
        return mime;
    }

    public void setMime(String mime) {
        this.mime = mime;
    }

    public long getSize() {
        return size;
    }

    public void setSize(long size) {
        this.size = size;
    }

    public String getEtag() {
        return etag;
    }

    public void setEtag(String etag) {
        this.etag = etag;
    }
}

