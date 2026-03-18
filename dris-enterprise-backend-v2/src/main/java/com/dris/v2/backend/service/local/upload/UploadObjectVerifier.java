package com.dris.v2.backend.service.local.upload;

import com.dris.v2.backend.config.BackendV2Properties;
import org.springframework.stereotype.Component;

import java.util.Locale;

@Component
final class UploadObjectVerifier {
    private final BackendV2Properties properties;
    private final ObjectStorageGateway storageGateway;

    UploadObjectVerifier(BackendV2Properties properties, ObjectStorageGateway storageGateway) {
        this.properties = properties;
        this.storageGateway = storageGateway;
    }

    Result verify(UploadSessionRecord session) {
        if (properties == null || session == null) {
            return Result.failed("OBJECT_VERIFY_FAILED", "upload session is required");
        }
        if (!StorageAddressSupport.isCos(properties)) {
            return Result.skipped("storage verification skipped for provider: " + safe(properties.getStorageProvider()));
        }
        try {
            StorageObjectStat stat = storageGateway.statObject(session.getBucket(), session.getObjectKey());
            long expectedSize = session.getExpectedSize();
            long actualSize = stat == null ? -1L : stat.getContentLength();
            String actualHash = normalizeSha256(stat == null ? "" : stat.getSha256());
            String etag = stat == null ? "" : safe(stat.getEtag());
            if (actualSize >= 0 && expectedSize >= 0 && actualSize != expectedSize) {
                return Result.failed("OBJECT_SIZE_MISMATCH", "expected size " + expectedSize + " but got " + actualSize)
                        .withActualSize(actualSize)
                        .withActualHash(actualHash)
                        .withEtag(etag);
            }
            String expectedHash = normalizeSha256(session.getExpectedSha256());
            if (!isBlank(expectedHash)) {
                if (isBlank(actualHash)) {
                    return Result.failed("OBJECT_HASH_MISMATCH", "object sha256 is unavailable for verification")
                            .withActualSize(actualSize)
                            .withEtag(etag);
                }
                if (!expectedHash.equalsIgnoreCase(actualHash)) {
                    return Result.failed("OBJECT_HASH_MISMATCH", "expected sha256 does not match actual object")
                            .withActualSize(actualSize)
                            .withActualHash(actualHash)
                            .withEtag(etag);
                }
            }
            return Result.verified(actualSize, actualHash, etag);
        } catch (StorageAccessException exception) {
            return Result.failed(exception.getCode(), safeMessage(exception));
        } catch (RuntimeException exception) {
            return Result.failed("OBJECT_VERIFY_FAILED", safeMessage(exception));
        }
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

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private String safe(String value) {
        return value == null ? "" : value.trim();
    }

    private String safeMessage(Throwable throwable) {
        return throwable == null || throwable.getMessage() == null || throwable.getMessage().trim().isEmpty()
                ? "storage verify failed"
                : throwable.getMessage().trim();
    }

    static final class Result {
        private final boolean verified;
        private final boolean skipped;
        private final String code;
        private final String message;
        private long actualSize = -1L;
        private String actualHash = "";
        private String etag = "";

        private Result(boolean verified, boolean skipped, String code, String message) {
            this.verified = verified;
            this.skipped = skipped;
            this.code = code == null ? "" : code;
            this.message = message == null ? "" : message;
        }

        static Result verified(long actualSize, String actualHash, String etag) {
            Result result = new Result(true, false, "", "ok");
            result.actualSize = actualSize;
            result.actualHash = actualHash == null ? "" : actualHash;
            result.etag = etag == null ? "" : etag;
            return result;
        }

        static Result skipped(String message) {
            return new Result(false, true, "", message);
        }

        static Result failed(String code, String message) {
            return new Result(false, false, code, message);
        }

        Result withActualSize(long actualSize) {
            this.actualSize = actualSize;
            return this;
        }

        Result withActualHash(String actualHash) {
            this.actualHash = actualHash == null ? "" : actualHash;
            return this;
        }

        Result withEtag(String etag) {
            this.etag = etag == null ? "" : etag;
            return this;
        }

        boolean isVerified() {
            return verified;
        }

        boolean isSkipped() {
            return skipped;
        }

        String getCode() {
            return code;
        }

        String getMessage() {
            return message;
        }

        long getActualSize() {
            return actualSize;
        }

        String getActualHash() {
            return actualHash;
        }

        String getEtag() {
            return etag;
        }
    }
}
