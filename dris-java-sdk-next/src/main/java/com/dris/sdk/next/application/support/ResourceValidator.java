package com.dris.sdk.next.application.support;

import com.dris.sdk.next.domain.Resource;
import com.dris.sdk.next.enumtype.ComponentType;
import com.dris.sdk.next.exception.ValidationException;

public class ResourceValidator {
    public void validate(Resource resource) {
        if (resource == null) {
            throw new ValidationException("resource is required");
        }
        ComponentType componentType = ComponentType.fromValue(resource.getComponentType());
        if (componentType == null) {
            throw new ValidationException("componentType must be resource/tool/service");
        }
        requireText(resource.getStorageType(), "storageType is required");
        requireText(resource.getAccessMethod(), "accessMethod is required");
        requireText(resource.getContentType(), "contentType is required");
        requireText(resource.getFormat(), "format is required");
        requireText(resource.getLocator(), "locator is required");

        if (componentType == ComponentType.RESOURCE) {
            String storageType = resource.getStorageType().trim();
            if (!isAllowedResourceStorageType(storageType)) {
                throw new ValidationException("resource.storageType must be one of external_uri/cos/s3/local");
            }
            if (!"download".equals(resource.getAccessMethod())) {
                throw new ValidationException("resource.accessMethod must be download");
            }
            return;
        }
        if (componentType == ComponentType.TOOL) {
            if (!"tool".equals(resource.getContentType())) {
                throw new ValidationException("tool.contentType must be tool");
            }
            return;
        }
        if (!"api_service".equals(resource.getStorageType())) {
            throw new ValidationException("service.storageType must be api_service");
        }
        if (!"api_call".equals(resource.getAccessMethod())) {
            throw new ValidationException("service.accessMethod must be api_call");
        }
        if (!"service".equals(resource.getContentType())) {
            throw new ValidationException("service.contentType must be service");
        }
        if (!(resource.getLocator().startsWith("http://") || resource.getLocator().startsWith("https://"))) {
            throw new ValidationException("service.locator must start with http:// or https://");
        }
    }

    private boolean isAllowedResourceStorageType(String storageType) {
        return "external_uri".equals(storageType)
                || "cos".equals(storageType)
                || "s3".equals(storageType)
                || "local".equals(storageType);
    }

    private void requireText(String value, String message) {
        if (value == null || value.trim().isEmpty()) {
            throw new ValidationException(message);
        }
    }
}
