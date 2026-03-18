package com.dris.v2.backend.service.local.upload;

import com.dris.sdk.next.domain.Resource;
import com.dris.v2.backend.contract.upload.CreateUploadSessionRequest;
import com.dris.v2.backend.contract.upload.DownloadPresignRequest;
import com.dris.v2.backend.contract.upload.DownloadPresignResult;
import com.dris.v2.backend.contract.DrisResponse;
import com.dris.v2.backend.contract.upload.UploadCreateSessionResult;

public interface LocalUploadService {
    DrisResponse<UploadCreateSessionResult> createUploadSession(String identifier, CreateUploadSessionRequest request);

    DrisResponse<Resource> completeUpload(String uploadId);

    DrisResponse<DownloadPresignResult> createDownloadPresign(DownloadPresignRequest request);
}