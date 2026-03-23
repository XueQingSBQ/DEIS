package com.dris.v2.backend.service.local.upload;

import com.dris.sdk.next.domain.Resource;
import com.dris.v2.backend.contract.upload.CreateUploadSessionRequest;
import com.dris.v2.backend.contract.upload.DownloadPresignRequest;
import com.dris.v2.backend.contract.upload.DownloadPresignResult;
import com.dris.v2.backend.contract.DrisResponse;
import com.dris.v2.backend.contract.upload.PresignedPut;
import com.dris.v2.backend.contract.upload.UploadCreateSessionResult;
import com.dris.sdk.next.domain.DigitalObject;
import com.dris.sdk.next.enumtype.ObjectStatus;
import com.dris.sdk.next.exception.NotFoundException;
import com.dris.v2.backend.config.BackendV2Properties;
import com.dris.v2.backend.metrics.BackendOperationMetrics;
import com.dris.v2.backend.sdknext.BackendKernelResponseSupport;
import com.dris.v2.backend.sdknext.LocalDrisKernelFacade;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Service
public class DefaultLocalUploadService implements LocalUploadService {
    private final LocalDrisKernelFacade localDrisKernelFacade;
    private final UploadSessionDao uploadSessionDao;
    private final BackendV2Properties properties;
    private final BackendOperationMetrics metrics;
    private final ObjectStorageGateway storageGateway;
    private final UploadObjectVerifier uploadObjectVerifier;

    public DefaultLocalUploadService(
            LocalDrisKernelFacade localDrisKernelFacade,
            UploadSessionDao uploadSessionDao,
            BackendV2Properties properties,
            BackendOperationMetrics metrics,
            ObjectStorageGateway storageGateway,
            UploadObjectVerifier uploadObjectVerifier
    ) {
        this.localDrisKernelFacade = localDrisKernelFacade;
        this.uploadSessionDao = uploadSessionDao;
        this.properties = properties;
        this.metrics = metrics;
        this.storageGateway = storageGateway;
        this.uploadObjectVerifier = uploadObjectVerifier;
    }

    @Override
    public DrisResponse<UploadCreateSessionResult> createUploadSession(String identifier, CreateUploadSessionRequest request) {
        long start = System.nanoTime();
        boolean ok = false;
        try {
            if (isBlank(identifier)) {
                return failure("VALIDATION_ERROR", "identifier is required");
            }
            if (request == null) {
                return failure("VALIDATION_ERROR", "request is required");
            }
            try {
                request.validate();
            } catch (Exception exception) {
                return failure("VALIDATION_ERROR", safeMessage(exception));
            }
            UploadProfile profile = UploadProfiles.byName(request.getProfile());
            if (profile == null) {
                return failure("VALIDATION_ERROR", "unknown upload profile: " + safe(request.getProfile()));
            }
            if ("service".equals(profile.getComponentType())) {
                return failure("VALIDATION_ERROR", "service component should be bound via PUT /api/v2/identifiers/{identifier}/component");
            }
            if (profile.isRequireSha256() && isBlank(request.getSha256())) {
                return failure("VALIDATION_ERROR", "sha256 is required for profile: " + profile.getName());
            }
            if (profile.getMaxSizeBytes() > 0 && request.getSize() > profile.getMaxSizeBytes()) {
                return failure("VALIDATION_ERROR", "file too large for profile: " + profile.getName());
            }

            DigitalObject object = localDrisKernelFacade.getDigitalObject(identifier.trim());
            if (object == null || object.getStatus() == ObjectStatus.DELETED) {
                return failure("NOT_FOUND", "identifier not found");
            }
            if (object.getResource() != null
                    && !properties.isAllowComponentTypeReplace()
                    && !profile.getComponentType().equals(object.getResource().getComponentType())) {
                return failure("CONFLICT", "component exists (" + object.getResource().getComponentType() + "), cannot upload as " + profile.getComponentType());
            }

            UploadSessionRecord session = new UploadSessionRecord();
            session.setUploadId(nextUploadId());
            session.setIdentifier(identifier.trim());
            session.setComponentType(profile.getComponentType());
            session.setBucket(safeBucket());
            session.setObjectKey(makeObjectKey(identifier.trim(), profile.getComponentType(), session.getUploadId(), request.getFilename(), profile.getFormat()));
            session.setUploadMode("single");
            session.setExpectedSize(request.getSize());
            session.setExpectedSha256(safe(request.getSha256()));
            session.setStatus("init");
            session.setExpiresAt(Instant.now().plusSeconds(Math.max(1, properties.getUploadSessionTtlSeconds())).toString());
            Map<String, Object> extra = new LinkedHashMap<String, Object>();
            extra.put("profile", profile.getName());
            extra.put("contentType", profile.getContentType());
            extra.put("format", profile.getFormat());
            extra.put("mime", profile.getMime());
            extra.put("filename", safe(request.getFilename()));
            session.setExtra(extra);
            PresignedPut put;
            try {
                put = storageGateway.createUploadPut(session);
            } catch (StorageAccessException exception) {
                return failure(exception.getCode(), safeMessage(exception));
            }
            uploadSessionDao.upsert(session);

            UploadCreateSessionResult result = new UploadCreateSessionResult();
            result.setUploadId(session.getUploadId());
            result.setBucket(session.getBucket());
            result.setObjectKey(session.getObjectKey());
            result.setPut(put);
            ok = true;
            return DrisResponse.success(result);
        } catch (RuntimeException exception) {
            return BackendKernelResponseSupport.failure(exception);
        } finally {
            metrics.record("createUploadSession", System.nanoTime() - start, ok);
        }
    }

    @Override
    public DrisResponse<Resource> completeUpload(String uploadId) {
        long start = System.nanoTime();
        boolean ok = false;
        try {
            if (isBlank(uploadId)) {
                return failure("VALIDATION_ERROR", "uploadId is required");
            }
            UploadSessionRecord session = uploadSessionDao.findByUploadId(uploadId.trim());
            if (session == null) {
                return failure("NOT_FOUND", "upload session not found");
            }
            DigitalObject object = localDrisKernelFacade.getDigitalObject(session.getIdentifier());
            if (object == null || object.getStatus() == ObjectStatus.DELETED) {
                return failure("NOT_FOUND", "identifier not found");
            }
            if (isExpired(session.getExpiresAt())) {
                session.setStatus("expired");
                persistSessionExtra(session, "FAILED", "upload session expired", -1L, "", "", Instant.now().toString());
                uploadSessionDao.upsert(session);
                return failure("UPLOAD_SESSION_EXPIRED", "upload session expired");
            }
            if ("done".equalsIgnoreCase(session.getStatus())) {
                try {
                    Resource current = toLegacyResource(localDrisKernelFacade.getResource(session.getIdentifier()));
                    ok = true;
                    return DrisResponse.success(current);
                } catch (NotFoundException exception) {
                    return failure("UPLOAD_INCONSISTENT", "component missing but session done");
                }
            }
            if ("failed".equalsIgnoreCase(session.getStatus()) || "expired".equalsIgnoreCase(session.getStatus())) {
                return failure("UPLOAD_SESSION_INVALID", "upload session is not completable");
            }

            session.setStatus("committing");
            uploadSessionDao.upsert(session);

            UploadObjectVerifier.Result verifyResult = verifyObject(session);
            if (!verifyResult.isVerified() && !verifyResult.isSkipped()) {
                session.setStatus("failed");
                persistSessionExtra(session, "FAILED", verifyResult.getMessage(), verifyResult.getActualSize(), verifyResult.getActualHash(), verifyResult.getEtag(), Instant.now().toString());
                uploadSessionDao.upsert(session);
                return failure(verifyResult.getCode(), verifyResult.getMessage());
            }

            String verifyStatus = verifyResult.isVerified() ? "VERIFIED" : "SKIPPED";
            com.dris.sdk.next.domain.Resource bindResource = buildBindResource(session, verifyResult, verifyStatus);
            try {
                localDrisKernelFacade.upsertResource(session.getIdentifier(), bindResource, false);
            } catch (RuntimeException exception) {
                session.setStatus("failed");
                persistSessionExtra(session, verifyStatus, safeMessage(exception), verifyResult.getActualSize(), verifyResult.getActualHash(), verifyResult.getEtag(), Instant.now().toString());
                uploadSessionDao.upsert(session);
                return BackendKernelResponseSupport.failure(exception);
            }

            com.dris.sdk.next.domain.Resource current = localDrisKernelFacade.getResource(session.getIdentifier());
            session.setStatus("done");
            persistSessionExtra(session, verifyStatus, "ok", verifyResult.getActualSize(), verifyResult.getActualHash(), verifyResult.getEtag(), Instant.now().toString());
            uploadSessionDao.upsert(session);
            ok = true;
            return DrisResponse.success(toLegacyResource(current));
        } catch (RuntimeException exception) {
            return BackendKernelResponseSupport.failure(exception);
        } finally {
            metrics.record("completeUpload", System.nanoTime() - start, ok);
        }
    }

    @Override
    public DrisResponse<DownloadPresignResult> createDownloadPresign(DownloadPresignRequest request) {
        long start = System.nanoTime();
        boolean ok = false;
        try {
            if (request == null) {
                return failure("VALIDATION_ERROR", "request is required");
            }
            try {
                request.validate();
            } catch (Exception exception) {
                return failure("VALIDATION_ERROR", safeMessage(exception));
            }
            DigitalObject object = localDrisKernelFacade.getDigitalObject(request.getIdentifier().trim());
            if (object == null || object.getStatus() == ObjectStatus.DELETED || object.getResource() == null) {
                return failure("NOT_FOUND", "resource not found");
            }
            com.dris.sdk.next.domain.Resource resource = object.getResource();
            if ("service".equals(resource.getComponentType())) {
                return failure("UNSUPPORTED_COMPONENT_TYPE", "service component does not support download");
            }
            if ("tool".equals(resource.getComponentType()) && "local_exec".equals(resource.getStorageType())) {
                return failure("UNSUPPORTED_COMPONENT_TYPE", "local_exec tool does not support download");
            }
            DownloadPresignResult result = new DownloadPresignResult();
            if (isDirectHttpDownload(resource)) {
                result.setUrl(resource.getLocator());
            } else {
                String objectKey = extractObjectKey(resource);
                if (objectKey.isEmpty()) {
                    return failure("OBJECTKEY_MISSING", "resource objectKey is missing");
                }
                int expiresInSeconds = clampExpires(
                        request.getExpiresInSeconds(),
                        properties.getDownloadPresignDefaultSeconds(),
                        properties.getDownloadPresignMaxSeconds()
                );
                try {
                    String downloadFilename = resolveDownloadFilename(object, resource);
                    String downloadContentType = safe(resource.getContentType());
                    result.setUrl(storageGateway.createDownloadUrl(extractBucket(resource), objectKey, expiresInSeconds, downloadFilename, downloadContentType));
                } catch (StorageAccessException exception) {
                    return failure(exception.getCode(), safeMessage(exception));
                }
                result.setExpiresInSeconds(expiresInSeconds);
            }
            if (result.getExpiresInSeconds() <= 0) {
                result.setExpiresInSeconds(clampExpires(
                        request.getExpiresInSeconds(),
                        properties.getDownloadPresignDefaultSeconds(),
                        properties.getDownloadPresignMaxSeconds()
                ));
            }
            result.setFilename(resolveDownloadFilename(object, resource));
            Object mime = resource.getExtra() == null ? null : resource.getExtra().get("mime");
            result.setMime(mime == null ? safe(resource.getContentType()) : String.valueOf(mime));
            result.setSize(resource.getSize());
            result.setEtag(safe(resource.getEtag()));
            ok = true;
            return DrisResponse.success(result);
        } catch (RuntimeException exception) {
            return BackendKernelResponseSupport.failure(exception);
        } finally {
            metrics.record("createDownloadPresign", System.nanoTime() - start, ok);
        }
    }

    private UploadObjectVerifier.Result verifyObject(UploadSessionRecord session) {
        if (session == null || "service".equals(session.getComponentType())) {
            return UploadObjectVerifier.Result.skipped("service upload verification skipped");
        }
        return uploadObjectVerifier.verify(session);
    }

    private com.dris.sdk.next.domain.Resource buildBindResource(UploadSessionRecord session, UploadObjectVerifier.Result verifyResult, String verifyStatus) {
        com.dris.sdk.next.domain.Resource resource = new com.dris.sdk.next.domain.Resource();
        resource.setComponentType(session.getComponentType());
        resource.setStorageType(resolveManagedStorageType());
        resource.setAccessMethod("download");
        resource.setContentType(String.valueOf(session.getExtra().get("contentType")));
        resource.setFormat(String.valueOf(session.getExtra().get("format")));
        resource.setLocator(StorageAddressSupport.locator(properties, session.getBucket(), session.getObjectKey()));
        long actualSize = verifyResult == null ? -1L : verifyResult.getActualSize();
        resource.setSize(actualSize >= 0 ? actualSize : session.getExpectedSize());
        resource.setEtag(verifyResult == null ? "" : safe(verifyResult.getEtag()));
        resource.setVersionId("");
        resource.setCreatedAt(Instant.now().toString());
        String hash = verifyResult == null ? "" : safe(verifyResult.getActualHash());
        if (isBlank(hash) && !isBlank(session.getExpectedSha256())) {
            hash = session.getExpectedSha256().trim();
        }
        resource.setHashValue(isBlank(hash) ? "" : prefixSha256(hash));
        Map<String, Object> extra = new LinkedHashMap<String, Object>();
        extra.put("profile", session.getExtra().get("profile"));
        extra.put("filename", session.getExtra().get("filename"));
        extra.put("mime", session.getExtra().get("mime"));
        extra.put("bucket", session.getBucket());
        extra.put("objectKey", session.getObjectKey());
        extra.put("uploadId", session.getUploadId());
        extra.put("_sourceUploadId", session.getUploadId());
        extra.put("_verifyStatus", verifyStatus);
        extra.put("_bindMode", "upload");
        resource.setExtra(extra);
        return resource;
    }

    private String resolveManagedStorageType() {
        String provider = safe(properties == null ? "" : properties.getStorageProvider()).toLowerCase(Locale.ROOT).trim();
        if (isBlank(provider)) {
            return "local";
        }
        if ("cos".equals(provider) || "s3".equals(provider) || "local".equals(provider)) {
            return provider;
        }
        return provider;
    }
    private void persistSessionExtra(
            UploadSessionRecord session,
            String verifyStatus,
            String verifyMessage,
            long actualSize,
            String actualHash,
            String etag,
            String completedAt
    ) {
        Map<String, Object> extra = session.getExtra() == null
                ? new LinkedHashMap<String, Object>()
                : new LinkedHashMap<String, Object>(session.getExtra());
        extra.put("_verifyStatus", safe(verifyStatus));
        extra.put("_verifyMessage", safe(verifyMessage));
        if (actualSize >= 0) {
            extra.put("_actualSize", Long.valueOf(actualSize));
        }
        if (!isBlank(actualHash)) {
            extra.put("_actualHash", prefixSha256(actualHash));
        }
        if (!isBlank(etag)) {
            extra.put("_etag", etag.trim());
        }
        if (!isBlank(completedAt)) {
            extra.put("_completedAt", completedAt.trim());
        }
        session.setExtra(extra);
    }

    private Resource toLegacyResource(com.dris.sdk.next.domain.Resource source) {
        Resource target = new Resource();
        if (source == null) {
            return target;
        }
        target.setComponentType(source.getComponentType());
        target.setStorageType(source.getStorageType());
        target.setAccessMethod(source.getAccessMethod());
        target.setContentType(source.getContentType());
        target.setFormat(source.getFormat());
        target.setLocator(source.getLocator());
        target.setSize(source.getSize());
        target.setHashValue(source.getHashValue());
        target.setEtag(source.getEtag());
        target.setVersionId(source.getVersionId());
        target.setCreatedAt(source.getCreatedAt());
        target.setExtra(source.getExtra());
        return target;
    }

    private String extractBucket(com.dris.sdk.next.domain.Resource resource) {
        if (resource != null && resource.getExtra() != null) {
            Object bucket = resource.getExtra().get("bucket");
            if (bucket != null && !String.valueOf(bucket).trim().isEmpty()) {
                return String.valueOf(bucket).trim();
            }
        }
        return safeBucket();
    }

    private String extractObjectKey(com.dris.sdk.next.domain.Resource resource) {
        if (resource == null) {
            return "";
        }
        if (resource.getExtra() != null && resource.getExtra().containsKey("objectKey")) {
            Object objectKey = resource.getExtra().get("objectKey");
            return objectKey == null ? "" : String.valueOf(objectKey).trim();
        }
        String locator = resource.getLocator();
        if (locator == null) {
            return "";
        }
        if (locator.startsWith("local://")) {
            int slash = locator.indexOf('/', "local://".length());
            if (slash >= 0 && slash + 1 < locator.length()) {
                return locator.substring(slash + 1);
            }
        }
        if (locator.startsWith("s3://") || locator.startsWith("cos://")) {
            int prefixLength = locator.startsWith("s3://") ? "s3://".length() : "cos://".length();
            int slash = locator.indexOf('/', prefixLength);
            if (slash >= 0 && slash + 1 < locator.length()) {
                return locator.substring(slash + 1);
            }
        }
        return StorageAddressSupport.extractObjectKey(locator);
    }

    private boolean isDirectHttpDownload(com.dris.sdk.next.domain.Resource resource) {
        if (resource == null || resource.getLocator() == null) {
            return false;
        }
        return resource.getLocator().startsWith("http://") || resource.getLocator().startsWith("https://");
    }

    private int clampExpires(int requested, int defaultValue, int maxValue) {
        int value = requested;
        if (value <= 0) {
            value = defaultValue;
        }
        if (maxValue > 0 && value > maxValue) {
            value = maxValue;
        }
        return Math.max(1, value);
    }

    private String nextUploadId() {
        return "up-" + System.currentTimeMillis() + '-' + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
    }

    private String makeObjectKey(String identifier, String componentType, String uploadId, String filename, String format) {
        String suffix = "tool".equals(componentType) ? "tool" : "resource";
        String extension = resolveObjectExtension(filename, format);
        return "institutions/"
                + currentInstitutionFolder()
                + "/identifiers/"
                + sanitizePathSegment(identifier)
                + "/"
                + suffix
                + "/"
                + sanitizePathSegment(uploadId)
                + extension;
    }

    private String currentInstitutionFolder() {
        String regionCode = sanitizePathSegment(safe(properties == null ? "" : properties.getRegionCode()));
        String organizationCode = sanitizePathSegment(safe(properties == null ? "" : properties.getOrganizationCode()));
        String combined = regionCode + organizationCode;
        if (!isBlank(combined)) {
            return combined;
        }
        String nodeId = sanitizePathSegment(safe(properties == null ? "" : properties.getNodeId()));
        return isBlank(nodeId) ? "node-local" : nodeId;
    }

    private String resolveObjectExtension(String filename, String format) {
        String extension = extractFilenameExtension(filename);
        if (!isBlank(extension)) {
            return "." + extension;
        }
        String normalizedFormat = normalizeExtension(format);
        return isBlank(normalizedFormat) ? "" : "." + normalizedFormat;
    }

    private String extractFilenameExtension(String filename) {
        String value = safe(filename);
        if (isBlank(value)) {
            return "";
        }
        int slash = Math.max(value.lastIndexOf('/'), value.lastIndexOf('\\'));
        if (slash >= 0 && slash < value.length() - 1) {
            value = value.substring(slash + 1);
        }
        int dot = value.lastIndexOf('.');
        if (dot < 0 || dot == value.length() - 1) {
            return "";
        }
        return normalizeExtension(value.substring(dot + 1));
    }

    private String normalizeExtension(String value) {
        String normalized = safe(value).toLowerCase(Locale.ROOT);
        if (isBlank(normalized)) {
            return "";
        }
        if ("openapi_json".equals(normalized)) {
            return "json";
        }
        if ("openapi_yaml".equals(normalized)) {
            return "yaml";
        }
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < normalized.length(); i++) {
            char ch = normalized.charAt(i);
            if ((ch >= 'a' && ch <= 'z') || (ch >= '0' && ch <= '9')) {
                builder.append(ch);
            }
        }
        return builder.toString();
    }

    private String sanitizePathSegment(String value) {
        String input = safe(value);
        if (isBlank(input)) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < input.length(); i++) {
            char ch = input.charAt(i);
            if ((ch >= 'a' && ch <= 'z')
                    || (ch >= 'A' && ch <= 'Z')
                    || (ch >= '0' && ch <= '9')
                    || ch == '-'
                    || ch == '_') {
                builder.append(ch);
            } else {
                builder.append('_');
            }
        }
        String output = builder.toString();
        while (output.contains("__")) {
            output = output.replace("__", "_");
        }
        return output;
    }

    private String resolveDownloadFilename(DigitalObject object, com.dris.sdk.next.domain.Resource resource) {
        String filename = stringValue(resource == null || resource.getExtra() == null ? null : resource.getExtra().get("filename"));
        if (!isBlank(filename)) {
            return filename;
        }
        String baseName = object == null || object.getMetadata() == null ? "" : safe(object.getMetadata().getName());
        if (!isBlank(baseName) && hasFilenameExtension(baseName)) {
            return baseName;
        }
        String extension = normalizeExtension(resource == null ? "" : resource.getFormat());
        if (!isBlank(baseName) && !isBlank(extension)) {
            return baseName + "." + extension;
        }
        String objectKey = resource == null ? "" : extractObjectKey(resource);
        String objectName = lastPathSegment(objectKey);
        if (!isBlank(objectName)) {
            return objectName;
        }
        return baseName;
    }

    private boolean hasFilenameExtension(String filename) {
        String value = safe(filename);
        int slash = Math.max(value.lastIndexOf('/'), value.lastIndexOf('\\'));
        int dot = value.lastIndexOf('.');
        return dot > slash && dot < value.length() - 1;
    }

    private String lastPathSegment(String value) {
        String path = safe(value);
        if (isBlank(path)) {
            return "";
        }
        int slash = path.lastIndexOf('/');
        return slash >= 0 && slash < path.length() - 1 ? path.substring(slash + 1) : path;
    }

    private String stringValue(Object value) {
        return value == null ? "" : safe(String.valueOf(value));
    }

    private boolean isExpired(String expiresAt) {
        if (isBlank(expiresAt)) {
            return false;
        }
        try {
            return Instant.parse(expiresAt).isBefore(Instant.now());
        } catch (Exception ignore) {
            return false;
        }
    }

    private String prefixSha256(String value) {
        if (isBlank(value)) {
            return "";
        }
        String normalized = value.trim();
        if (normalized.startsWith("sha256:")) {
            return normalized;
        }
        return "sha256:" + normalized;
    }

    private DrisResponse failure(String code, String message) {
        return DrisResponse.failure(code, message, null);
    }

    private String safeBucket() {
        return isBlank(properties.getDefaultBucket()) ? "local-bucket" : properties.getDefaultBucket().trim();
    }

    private String safe(String value) {
        return value == null ? "" : value.trim();
    }

    private String safeMessage(Throwable throwable) {
        return throwable == null || throwable.getMessage() == null || throwable.getMessage().trim().isEmpty()
                ? "internal error"
                : throwable.getMessage().trim();
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}

