package com.dris.v2.backend.service.local.upload;

import com.dris.v2.backend.config.BackendV2Properties;
import com.dris.v2.backend.contract.upload.PresignedPut;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DefaultObjectStorageGatewayTest {
    @Test
    void createUploadPut_localProvider_shouldReturnLocalAddress() {
        BackendV2Properties properties = new BackendV2Properties();
        properties.setStorageProvider("local");
        DefaultObjectStorageGateway gateway = new DefaultObjectStorageGateway(properties);

        PresignedPut put = gateway.createUploadPut(sampleSession());

        assertTrue(put.getUrl().startsWith("local://upload/"));
        assertEquals("application/json", put.getHeaders().get("Content-Type"));
        assertEquals("dataset-1", put.getHeaders().get("identifier"));
        gateway.shutdown();
    }

    @Test
    void createUploadPut_cosProvider_shouldReturnSignedUrl() {
        BackendV2Properties properties = new BackendV2Properties();
        properties.setStorageProvider("cos");
        properties.setStorageRegion("ap-beijing");
        properties.setStorageEndpoint("https://cos.ap-beijing.myqcloud.com");
        properties.setDefaultBucket("demo-bucket-1250000000");
        properties.setStorageSecretId("test-secret-id");
        properties.setStorageSecretKey("test-secret-key");
        DefaultObjectStorageGateway gateway = new DefaultObjectStorageGateway(properties);

        PresignedPut put = gateway.createUploadPut(sampleSession());
        String downloadUrl = gateway.createDownloadUrl("demo-bucket-1250000000", "identifiers/dataset-1/resource/up-1", 300, "demo data.json", "application/json");

        assertTrue(put.getUrl().contains("demo-bucket-1250000000.cos.ap-beijing.myqcloud.com"));
        assertTrue(put.getUrl().contains("q-sign-algorithm="));
        assertEquals("application/json", put.getHeaders().get("Content-Type"));
        assertEquals("abc123", put.getHeaders().get("x-cos-meta-sha256"));
        assertTrue(downloadUrl.contains("q-sign-algorithm="));
        assertTrue(downloadUrl.contains("response-content-disposition="));
        assertTrue(downloadUrl.contains("filename*=UTF-8%27%27demo%20data.json") || downloadUrl.contains("filename%2A%3DUTF-8%27%27demo%2520data.json"));
        gateway.shutdown();
    }

    @Test
    void createUploadPut_cosProvider_withoutCredentials_shouldFailFast() {
        BackendV2Properties properties = new BackendV2Properties();
        properties.setStorageProvider("cos");
        properties.setStorageRegion("ap-beijing");
        properties.setDefaultBucket("demo-bucket-1250000000");
        DefaultObjectStorageGateway gateway = new DefaultObjectStorageGateway(properties);

        StorageAccessException exception = assertThrows(StorageAccessException.class, () -> gateway.createUploadPut(sampleSession()));

        assertEquals("STORAGE_CREDENTIALS_MISSING", exception.getCode());
        gateway.shutdown();
    }

    private UploadSessionRecord sampleSession() {
        UploadSessionRecord session = new UploadSessionRecord();
        session.setUploadId("up-1");
        session.setIdentifier("dataset-1");
        session.setBucket("demo-bucket-1250000000");
        session.setObjectKey("identifiers/dataset-1/resource/up-1");
        session.setExpectedSha256("abc123");
        Map<String, Object> extra = new LinkedHashMap<String, Object>();
        extra.put("profile", "data_json");
        extra.put("format", "json");
        extra.put("mime", "application/json");
        session.setExtra(extra);
        return session;
    }
}
