package com.dris.v2.backend.service.local.upload;

import com.dris.v2.backend.contract.upload.PresignedPut;

public interface ObjectStorageGateway {
    PresignedPut createUploadPut(UploadSessionRecord session);

    String createDownloadUrl(String bucket, String objectKey, int expiresInSeconds, String downloadFilename, String contentType);

    StorageObjectStat statObject(String bucket, String objectKey);
}
