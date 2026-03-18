package com.dris.v2.backend.service.local.upload;

import com.dris.v2.backend.config.BackendV2Properties;
import com.dris.v2.backend.contract.upload.PresignedPut;
import com.qcloud.cos.COSClient;
import com.qcloud.cos.ClientConfig;
import com.qcloud.cos.auth.BasicCOSCredentials;
import com.qcloud.cos.auth.BasicSessionCredentials;
import com.qcloud.cos.auth.COSCredentials;
import com.qcloud.cos.exception.CosClientException;
import com.qcloud.cos.exception.CosServiceException;
import com.qcloud.cos.http.HttpMethodName;
import com.qcloud.cos.http.HttpProtocol;
import com.qcloud.cos.model.ObjectMetadata;
import com.qcloud.cos.region.Region;
import org.springframework.stereotype.Service;

import javax.annotation.PreDestroy;
import java.net.URI;
import java.net.URL;
import java.net.URLEncoder;
import java.util.Collections;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

@Service
public class DefaultObjectStorageGateway implements ObjectStorageGateway {
    private final BackendV2Properties properties;
    private volatile COSClient cosClient;

    public DefaultObjectStorageGateway(BackendV2Properties properties) {
        this.properties = properties;
    }

    @Override
    public PresignedPut createUploadPut(UploadSessionRecord session) {
        if (session == null) {
            throw new StorageAccessException("VALIDATION_ERROR", "upload session is required");
        }
        int expiresInSeconds = Math.max(1, properties == null ? 900 : properties.getUploadPresignExpiresSeconds());
        Map<String, String> headers = StorageAddressSupport.isCos(properties)
                ? buildCosUploadHeaders(session)
                : buildLocalUploadHeaders(session);
        String url = StorageAddressSupport.isCos(properties)
                ? presignUploadUrl(session, headers, expiresInSeconds)
                : StorageAddressSupport.uploadUrl(properties, session);
        PresignedPut put = new PresignedPut();
        put.setUrl(url);
        put.setHeaders(Collections.unmodifiableMap(new LinkedHashMap<String, String>(headers)));
        put.setExpiresInSeconds(expiresInSeconds);
        return put;
    }

    @Override
    public String createDownloadUrl(String bucket, String objectKey, int expiresInSeconds, String downloadFilename, String contentType) {
        if (isBlank(objectKey)) {
            throw new StorageAccessException("OBJECTKEY_MISSING", "resource objectKey is missing");
        }
        if (!StorageAddressSupport.isCos(properties)) {
            return StorageAddressSupport.downloadUrl(properties, bucket, objectKey);
        }
        try {
            Map<String, String> requestParameters = buildCosDownloadQueryParameters(downloadFilename, contentType);
            URL url = getCosClient().generatePresignedUrl(
                    safe(bucket),
                    safe(objectKey),
                    new Date(System.currentTimeMillis() + Math.max(1, expiresInSeconds) * 1000L),
                    HttpMethodName.GET,
                    Collections.<String, String>emptyMap(),
                    requestParameters
            );
            return url == null ? "" : url.toString();
        } catch (CosClientException exception) {
            throw new StorageAccessException("DOWNLOAD_PRESIGN_FAILED", safeMessage(exception));
        }
    }

    @Override
    public StorageObjectStat statObject(String bucket, String objectKey) {
        if (isBlank(bucket) || isBlank(objectKey)) {
            throw new StorageAccessException("OBJECT_VERIFY_FAILED", "bucket and objectKey are required");
        }
        if (!StorageAddressSupport.isCos(properties)) {
            throw new StorageAccessException("OBJECT_VERIFY_FAILED", "storage verification is unavailable for provider: " + safe(properties == null ? "" : properties.getStorageProvider()));
        }
        try {
            ObjectMetadata metadata = getCosClient().getObjectMetadata(safe(bucket), safe(objectKey));
            return new StorageObjectStat(
                    metadata == null ? -1L : metadata.getContentLength(),
                    normalizeSha256(readSha256(metadata)),
                    stripQuotes(metadata == null ? "" : metadata.getETag())
            );
        } catch (CosServiceException exception) {
            if (exception.getStatusCode() == 404 || "NoSuchKey".equalsIgnoreCase(exception.getErrorCode())) {
                throw new StorageAccessException("OBJECT_NOT_FOUND", "object not found in storage");
            }
            if (exception.getStatusCode() == 403) {
                throw new StorageAccessException("OBJECT_VERIFY_FAILED", "storage verify access denied (403)");
            }
            throw new StorageAccessException("OBJECT_VERIFY_FAILED", safeMessage(exception));
        } catch (CosClientException exception) {
            throw new StorageAccessException("OBJECT_VERIFY_FAILED", safeMessage(exception));
        }
    }

    @PreDestroy
    public void shutdown() {
        COSClient client = this.cosClient;
        this.cosClient = null;
        if (client != null) {
            client.shutdown();
        }
    }

    private String presignUploadUrl(UploadSessionRecord session, Map<String, String> headers, int expiresInSeconds) {
        if (isBlank(session.getBucket()) || isBlank(session.getObjectKey())) {
            throw new StorageAccessException("UPLOAD_PRESIGN_FAILED", "bucket and objectKey are required for upload presign");
        }
        try {
            URL url = getCosClient().generatePresignedUrl(
                    safe(session.getBucket()),
                    safe(session.getObjectKey()),
                    new Date(System.currentTimeMillis() + Math.max(1, expiresInSeconds) * 1000L),
                    HttpMethodName.PUT,
                    headers,
                    Collections.<String, String>emptyMap()
            );
            return url == null ? "" : url.toString();
        } catch (CosClientException exception) {
            throw new StorageAccessException("UPLOAD_PRESIGN_FAILED", safeMessage(exception));
        }
    }

    private Map<String, String> buildCosDownloadQueryParameters(String downloadFilename, String contentType) {
        Map<String, String> params = new LinkedHashMap<String, String>();
        if (!isBlank(contentType)) {
            params.put("response-content-type", safe(contentType));
        }
        String safeFilename = sanitizeDownloadFilename(downloadFilename);
        if (!isBlank(safeFilename)) {
            params.put("response-content-disposition", buildContentDisposition(safeFilename));
        }
        return params;
    }

    private String buildContentDisposition(String filename) {
        String ascii = filename.replace("\\", "_").replace("\"", "_");
        String encoded = urlEncodeUtf8(filename);
        return "attachment; filename=\"" + ascii + "\"; filename*=UTF-8''" + encoded;
    }

    private String sanitizeDownloadFilename(String filename) {
        String value = safe(filename).trim();
        if (isBlank(value)) {
            return "";
        }
        return value.replace("/", "_").replace("\\", "_");
    }

    private String urlEncodeUtf8(String value) {
        try {
            return URLEncoder.encode(safe(value), "UTF-8").replace("+", "%20");
        } catch (Exception exception) {
            return safe(value);
        }
    }
    private COSClient getCosClient() {
        COSClient client = this.cosClient;
        if (client != null) {
            return client;
        }
        synchronized (this) {
            client = this.cosClient;
            if (client == null) {
                client = buildCosClient();
                this.cosClient = client;
            }
            return client;
        }
    }

    private COSClient buildCosClient() {
        String secretId = resolveConfiguredValue(properties == null ? "" : properties.getStorageSecretId(), "DRIS_STORAGE_SECRET_ID");
        String secretKey = resolveConfiguredValue(properties == null ? "" : properties.getStorageSecretKey(), "DRIS_STORAGE_SECRET_KEY");
        String sessionToken = resolveConfiguredValue(properties == null ? "" : properties.getStorageSessionToken(), "DRIS_STORAGE_SESSION_TOKEN");
        if (isBlank(secretId) || isBlank(secretKey)) {
            throw new StorageAccessException("STORAGE_CREDENTIALS_MISSING", "COS credentials are missing, please configure DRIS_STORAGE_SECRET_ID and DRIS_STORAGE_SECRET_KEY");
        }
        String region = resolveStorageRegion();
        COSCredentials credentials = isBlank(sessionToken)
                ? new BasicCOSCredentials(secretId, secretKey)
                : new BasicSessionCredentials(secretId, secretKey, sessionToken);
        ClientConfig clientConfig = new ClientConfig(new Region(region));
        clientConfig.setHttpProtocol(resolveHttpProtocol());
        String endpointSuffix = resolveEndpointSuffix();
        if (!isBlank(endpointSuffix)) {
            clientConfig.setEndPointSuffix(endpointSuffix);
        }
        return new COSClient(credentials, clientConfig);
    }

    private Map<String, String> buildCosUploadHeaders(UploadSessionRecord session) {
        Map<String, String> headers = new LinkedHashMap<String, String>();
        String mime = extractExtra(session, "mime");
        if (!isBlank(mime)) {
            headers.put("Content-Type", mime);
        }
        headers.put("x-cos-meta-identifier", safe(session.getIdentifier()));
        headers.put("x-cos-meta-profile", extractExtra(session, "profile"));
        headers.put("x-cos-meta-format", extractExtra(session, "format"));
        String sha256 = normalizeSha256(session.getExpectedSha256());
        if (!isBlank(sha256)) {
            headers.put("x-cos-meta-sha256", sha256);
        }
        return headers;
    }

    private Map<String, String> buildLocalUploadHeaders(UploadSessionRecord session) {
        Map<String, String> headers = new LinkedHashMap<String, String>();
        headers.put("identifier", safe(session.getIdentifier()));
        headers.put("profile", extractExtra(session, "profile"));
        headers.put("format", extractExtra(session, "format"));
        String mime = extractExtra(session, "mime");
        if (!isBlank(mime)) {
            headers.put("Content-Type", mime);
        }
        String sha256 = safe(session.getExpectedSha256());
        if (!isBlank(sha256)) {
            headers.put("sha256", sha256);
        }
        return headers;
    }

    private String readSha256(ObjectMetadata metadata) {
        if (metadata == null) {
            return "";
        }
        String value = firstNonBlank(
                metadata.getUserMetaDataOf("sha256"),
                metadata.getUserMetaDataOf("x-cos-meta-sha256")
        );
        if (!isBlank(value)) {
            return value;
        }
        Map<String, String> userMetadata = metadata.getUserMetadata();
        if (userMetadata == null || userMetadata.isEmpty()) {
            return "";
        }
        value = firstNonBlank(userMetadata.get("sha256"), userMetadata.get("x-cos-meta-sha256"));
        if (!isBlank(value)) {
            return value;
        }
        for (Map.Entry<String, String> entry : userMetadata.entrySet()) {
            if (entry != null && !isBlank(entry.getKey()) && "sha256".equalsIgnoreCase(entry.getKey().trim())) {
                return safe(entry.getValue());
            }
        }
        return "";
    }

    private String resolveStorageRegion() {
        String region = safe(properties == null ? "" : properties.getStorageRegion());
        if (!isBlank(region)) {
            return region;
        }
        String host = extractEndpointHost();
        int marker = host.indexOf(".cos.");
        if (marker >= 0) {
            int start = marker + ".cos.".length();
            int end = host.indexOf(".myqcloud.com", start);
            if (end > start) {
                return host.substring(start, end);
            }
        }
        if (host.startsWith("cos.")) {
            int start = "cos.".length();
            int end = host.indexOf(".myqcloud.com", start);
            if (end > start) {
                return host.substring(start, end);
            }
        }
        throw new StorageAccessException("STORAGE_CONFIG_ERROR", "storage region is missing for COS provider");
    }

    private String resolveEndpointSuffix() {
        String host = extractEndpointHost();
        if (isBlank(host)) {
            String region = safe(properties == null ? "" : properties.getStorageRegion());
            return isBlank(region) ? "" : "cos." + region + ".myqcloud.com";
        }
        String bucket = safe(properties == null ? "" : properties.getDefaultBucket());
        if (!isBlank(bucket) && host.startsWith(bucket + ".")) {
            return host.substring(bucket.length() + 1);
        }
        return host;
    }

    private HttpProtocol resolveHttpProtocol() {
        String endpoint = safe(properties == null ? "" : properties.getStorageEndpoint());
        if (endpoint.toLowerCase(Locale.ROOT).startsWith("http://")) {
            return HttpProtocol.http;
        }
        return HttpProtocol.https;
    }

    private String extractEndpointHost() {
        String endpoint = safe(properties == null ? "" : properties.getStorageEndpoint());
        if (isBlank(endpoint)) {
            return "";
        }
        try {
            URI uri = URI.create(endpoint);
            return safe(uri.getHost());
        } catch (Exception ignore) {
            return "";
        }
    }

    private String resolveConfiguredValue(String configured, String... envNames) {
        if (!isBlank(configured)) {
            return configured.trim();
        }
        if (envNames == null) {
            return "";
        }
        for (String envName : envNames) {
            if (isBlank(envName)) {
                continue;
            }
            String envValue = System.getenv(envName);
            if (!isBlank(envValue)) {
                return envValue.trim();
            }
        }
        return "";
    }

    private String extractExtra(UploadSessionRecord session, String key) {
        if (session == null || session.getExtra() == null || isBlank(key)) {
            return "";
        }
        Object value = session.getExtra().get(key);
        return value == null ? "" : String.valueOf(value).trim();
    }

    private String firstNonBlank(String first, String second) {
        if (!isBlank(first)) {
            return first.trim();
        }
        return isBlank(second) ? "" : second.trim();
    }

    private String normalizeSha256(String value) {
        if (isBlank(value)) {
            return "";
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        if (normalized.startsWith("sha256:")) {
            normalized = normalized.substring("sha256:".length());
        }
        return normalized;
    }

    private String stripQuotes(String value) {
        if (isBlank(value)) {
            return "";
        }
        String trimmed = value.trim();
        if (trimmed.length() >= 2 && trimmed.startsWith("\"") && trimmed.endsWith("\"")) {
            return trimmed.substring(1, trimmed.length() - 1);
        }
        return trimmed;
    }

    private String safeMessage(Throwable throwable) {
        return throwable == null || throwable.getMessage() == null || throwable.getMessage().trim().isEmpty()
                ? "storage operation failed"
                : throwable.getMessage().trim();
    }

    private String safe(String value) {
        return value == null ? "" : value.trim();
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}


