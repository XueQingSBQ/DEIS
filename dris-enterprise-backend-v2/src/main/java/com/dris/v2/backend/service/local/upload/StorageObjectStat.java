package com.dris.v2.backend.service.local.upload;

final class StorageObjectStat {
    private final long contentLength;
    private final String sha256;
    private final String etag;

    StorageObjectStat(long contentLength, String sha256, String etag) {
        this.contentLength = contentLength;
        this.sha256 = sha256 == null ? "" : sha256;
        this.etag = etag == null ? "" : etag;
    }

    long getContentLength() {
        return contentLength;
    }

    String getSha256() {
        return sha256;
    }

    String getEtag() {
        return etag;
    }
}
