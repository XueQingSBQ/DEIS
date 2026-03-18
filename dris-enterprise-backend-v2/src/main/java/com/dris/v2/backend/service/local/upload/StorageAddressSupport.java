package com.dris.v2.backend.service.local.upload;

import com.dris.v2.backend.config.BackendV2Properties;

import java.net.URI;

final class StorageAddressSupport {
    private StorageAddressSupport() {
    }

    static String uploadUrl(BackendV2Properties properties, UploadSessionRecord session) {
        if (!isCos(properties)) {
            return "local://upload/" + session.getUploadId();
        }
        String base = resolveCosBase(properties, session.getBucket());
        if (base.isEmpty()) {
            return "local://upload/" + session.getUploadId();
        }
        return appendPath(base, session.getObjectKey());
    }

    static String downloadUrl(BackendV2Properties properties, String bucket, String objectKey) {
        if (!isCos(properties)) {
            return "local://download/" + safe(objectKey);
        }
        String base = resolveCosBase(properties, bucket);
        if (base.isEmpty()) {
            return "local://download/" + safe(objectKey);
        }
        return appendPath(base, objectKey);
    }

    static String locator(BackendV2Properties properties, String bucket, String objectKey) {
        if (isCos(properties)) {
            return "cos://" + safe(bucket) + "/" + safe(objectKey);
        }
        return "local://" + safe(bucket) + "/" + safe(objectKey);
    }

    static String extractObjectKey(String locator) {
        if (locator == null || locator.trim().isEmpty()) {
            return "";
        }
        String value = locator.trim();
        String[] prefixes = new String[]{"local://", "s3://", "cos://"};
        for (String prefix : prefixes) {
            if (value.startsWith(prefix)) {
                int slash = value.indexOf('/', prefix.length());
                if (slash >= 0 && slash + 1 < value.length()) {
                    return value.substring(slash + 1);
                }
                return "";
            }
        }
        return "";
    }

    static boolean isCos(BackendV2Properties properties) {
        return properties != null && "cos".equalsIgnoreCase(safe(properties.getStorageProvider()));
    }

    private static String appendPath(String base, String path) {
        String safeBase = trimTrailingSlash(safe(base));
        if (safeBase.isEmpty()) {
            return "";
        }
        String safePath = trimLeadingSlash(safe(path));
        if (safePath.isEmpty()) {
            return safeBase;
        }
        return safeBase + "/" + safePath;
    }

    private static String resolveCosBase(BackendV2Properties properties, String bucket) {
        String safeBucket = safe(bucket);
        if (safeBucket.isEmpty()) {
            return "";
        }
        String publicBase = properties == null ? "" : safe(properties.getStoragePublicBaseUrl());
        if (!publicBase.isEmpty()) {
            return trimTrailingSlash(publicBase);
        }
        String endpoint = properties == null ? "" : safe(properties.getStorageEndpoint());
        if (!endpoint.isEmpty()) {
            try {
                URI uri = URI.create(endpoint);
                String scheme = safe(uri.getScheme());
                if (scheme.isEmpty()) {
                    scheme = "https";
                }
                String host = safe(uri.getHost());
                String path = safe(uri.getPath());
                if (host.isEmpty()) {
                    return "";
                }
                String cosHost = host;
                if (host.startsWith("cos.")) {
                    cosHost = safeBucket + "." + host;
                } else if (!host.startsWith(safeBucket + ".")) {
                    cosHost = safeBucket + "." + host;
                }
                String base = scheme + "://" + cosHost;
                path = trimTrailingSlash(path);
                if (!path.isEmpty() && !"/".equals(path)) {
                    base = base + "/" + trimLeadingSlash(path);
                }
                return base;
            } catch (Exception ignore) {
                return "";
            }
        }
        String region = properties == null ? "" : safe(properties.getStorageRegion());
        if (!region.isEmpty()) {
            return "https://" + safeBucket + ".cos." + region + ".myqcloud.com";
        }
        return "";
    }

    private static String trimTrailingSlash(String input) {
        int end = input == null ? 0 : input.length();
        while (end > 0 && input.charAt(end - 1) == '/') {
            end--;
        }
        return input == null ? "" : input.substring(0, end);
    }

    private static String trimLeadingSlash(String input) {
        int start = 0;
        while (input != null && start < input.length() && input.charAt(start) == '/') {
            start++;
        }
        return input == null ? "" : input.substring(start);
    }

    private static String safe(String value) {
        return value == null ? "" : value.trim();
    }
}