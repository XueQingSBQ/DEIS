package com.dris.v2.backend.service.local.upload;

public final class UploadProfile {
    private final String name;
    private final String componentType;
    private final String contentType;
    private final String format;
    private final String mime;
    private final boolean requireSha256;
    private final long maxSizeBytes;

    public UploadProfile(
            String name,
            String componentType,
            String contentType,
            String format,
            String mime,
            boolean requireSha256,
            long maxSizeBytes
    ) {
        this.name = name;
        this.componentType = componentType;
        this.contentType = contentType;
        this.format = format;
        this.mime = mime;
        this.requireSha256 = requireSha256;
        this.maxSizeBytes = maxSizeBytes;
    }

    public String getName() {
        return name;
    }

    public String getComponentType() {
        return componentType;
    }

    public String getContentType() {
        return contentType;
    }

    public String getFormat() {
        return format;
    }

    public String getMime() {
        return mime;
    }

    public boolean isRequireSha256() {
        return requireSha256;
    }

    public long getMaxSizeBytes() {
        return maxSizeBytes;
    }
}