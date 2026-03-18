package com.dris.v2.backend.contract.upload;

public class UploadCreateSessionResult {
    private String uploadId;
    private String bucket;
    private String objectKey;
    private PresignedPut put;

    public String getUploadId() {
        return uploadId;
    }

    public void setUploadId(String uploadId) {
        this.uploadId = uploadId;
    }

    public String getBucket() {
        return bucket;
    }

    public void setBucket(String bucket) {
        this.bucket = bucket;
    }

    public String getObjectKey() {
        return objectKey;
    }

    public void setObjectKey(String objectKey) {
        this.objectKey = objectKey;
    }

    public PresignedPut getPut() {
        return put;
    }

    public void setPut(PresignedPut put) {
        this.put = put;
    }
}

