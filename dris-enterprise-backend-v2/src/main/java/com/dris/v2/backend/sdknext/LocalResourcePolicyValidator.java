package com.dris.v2.backend.sdknext;

import com.dris.v2.backend.config.BackendV2Properties;
import com.dris.sdk.next.domain.Resource;
import com.dris.sdk.next.exception.ConflictException;
import com.dris.sdk.next.exception.ValidationException;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class LocalResourcePolicyValidator {
    private final BackendV2Properties properties;

    public LocalResourcePolicyValidator(BackendV2Properties properties) {
        this.properties = properties;
    }

    public void validateForBind(Resource existing, Resource incoming, boolean forceReplaceType) {
        if (incoming == null) {
            throw new ValidationException("resource is required");
        }
        if (existing != null) {
            String oldType = safe(existing.getComponentType());
            String newType = safe(incoming.getComponentType());
            boolean allowReplace = forceReplaceType || properties.isAllowComponentTypeReplace();
            if (!allowReplace && !oldType.isEmpty() && !newType.isEmpty() && !oldType.equals(newType)) {
                throw new ConflictException("component exists (" + oldType + "), replace type not allowed");
            }
        }
        if ("tool".equals(safe(incoming.getComponentType()))) {
            if (properties.isRequireToolSha256()) {
                String hashValue = safe(incoming.getHashValue());
                if (!hashValue.startsWith("sha256:")) {
                    throw new ValidationException("tool.hashValue must be sha256:<hex>");
                }
            }
            if (properties.isRequireToolSpec()) {
                Map<String, Object> extra = incoming.getExtra();
                if (extra == null || !extra.containsKey("argTemplate")) {
                    throw new ValidationException("tool.extra.argTemplate is required");
                }
                if (!extra.containsKey("input_schema")) {
                    throw new ValidationException("tool.extra.input_schema is required");
                }
                if (!extra.containsKey("output_schema")) {
                    throw new ValidationException("tool.extra.output_schema is required");
                }
            }
        }
    }

    private String safe(String value) {
        return value == null ? "" : value.trim();
    }
}
