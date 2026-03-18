package com.dris.v2.backend.control;

import com.dris.v2.backend.api.ApiResponses;
import com.dris.v2.backend.dto.ApiResponse;
import com.dris.v2.backend.service.IdentifierUploadService;
import com.dris.sdk.next.domain.Resource;
import com.dris.v2.backend.contract.upload.CreateUploadSessionRequest;
import com.dris.v2.backend.contract.upload.DownloadPresignRequest;
import com.dris.v2.backend.contract.upload.DownloadPresignResult;
import com.dris.v2.backend.contract.upload.UploadCreateSessionResult;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v2")
public class IdentifierUploadController {
    private final IdentifierUploadService service;

    public IdentifierUploadController(IdentifierUploadService service) {
        this.service = service;
    }

    @PostMapping("/identifiers/{identifier}/upload-sessions")
    public ResponseEntity<ApiResponse<UploadCreateSessionResult>> createUploadSession(
            @PathVariable("identifier") String identifier,
            @RequestBody CreateUploadSessionRequest request
    ) {
        return ApiResponses.fromDris(service.createUploadSession(identifier, request));
    }

    @PostMapping("/upload-sessions/{uploadId}/complete")
    public ResponseEntity<ApiResponse<Resource>> completeUpload(@PathVariable("uploadId") String uploadId) {
        return ApiResponses.fromDris(service.completeUpload(uploadId));
    }

    @PostMapping("/downloads/presign")
    public ResponseEntity<ApiResponse<DownloadPresignResult>> createDownloadPresign(
            @RequestBody DownloadPresignRequest request
    ) {
        return ApiResponses.fromDris(service.createDownloadPresign(request));
    }
}
