package com.dris.sdk.next.application.support;

import com.dris.sdk.next.domain.IdentifierMetadata;
import com.dris.sdk.next.exception.ValidationException;

import java.util.LinkedHashMap;
import java.util.Map;

public class MetadataPatchApplier {
    public IdentifierMetadata apply(IdentifierMetadata source, Map<String, Object> changes) {
        IdentifierMetadata target = source.copy();
        if (changes == null || changes.isEmpty()) {
            return target;
        }
        for (Map.Entry<String, Object> entry : changes.entrySet()) {
            applyField(target, entry.getKey(), entry.getValue());
        }
        return target;
    }

    private void applyField(IdentifierMetadata target, String field, Object value) {
        if ("owner".equals(field)) {
            target.setOwner(asString(value));
            return;
        }
        if ("description".equals(field)) {
            target.setDescription(asString(value));
            return;
        }
        if ("applicationScenario".equals(field)) {
            target.setApplicationScenario(asString(value));
            return;
        }
        if ("status".equals(field)) {
            target.setStatus(asString(value));
            return;
        }
        if ("dataCategory".equals(field)) {
            target.setDataCategory(asString(value));
            return;
        }
        if ("registrationType".equals(field)) {
            target.setRegistrationType(asString(value));
            return;
        }
        if ("industry".equals(field)) {
            target.setIndustry(asString(value));
            return;
        }
        if ("name".equals(field)) {
            target.setName(asString(value));
            return;
        }
        if ("entityType".equals(field)) {
            target.setEntityType(asString(value));
            return;
        }
        if ("additionalAttributes".equals(field)) {
            target.setAdditionalAttributes(asMap(value));
            return;
        }
        Map<String, Object> attrs = target.getAdditionalAttributes();
        if (attrs == null) {
            attrs = new LinkedHashMap<String, Object>();
        }
        attrs.put(field, value);
        target.setAdditionalAttributes(attrs);
    }

    private String asString(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> asMap(Object value) {
        if (value == null) {
            return new LinkedHashMap<String, Object>();
        }
        if (!(value instanceof Map)) {
            throw new ValidationException("additionalAttributes must be a map");
        }
        return new LinkedHashMap<String, Object>((Map<String, Object>) value);
    }
}