package com.dris.sdk.next.request;

import java.util.LinkedHashMap;
import java.util.Map;

public class RegisterIdentifierRequest {
    private String regionCode;
    private String organizationCode;
    private String industry;
    private String dataCategory;
    private String registrationType;
    private String owner;
    private String description;
    private String applicationScenario;
    private String name;
    private String entityType;
    private String idempotencyKey;
    private Map<String, Object> additionalAttributes = new LinkedHashMap<String, Object>();

    public String getRegionCode() {
        return regionCode;
    }

    public void setRegionCode(String regionCode) {
        this.regionCode = regionCode;
    }

    public String getOrganizationCode() {
        return organizationCode;
    }

    public void setOrganizationCode(String organizationCode) {
        this.organizationCode = organizationCode;
    }

    public String getIndustry() {
        return industry;
    }

    public void setIndustry(String industry) {
        this.industry = industry;
    }

    public String getDataCategory() {
        return dataCategory;
    }

    public void setDataCategory(String dataCategory) {
        this.dataCategory = dataCategory;
    }

    public String getRegistrationType() {
        return registrationType;
    }

    public void setRegistrationType(String registrationType) {
        this.registrationType = registrationType;
    }

    public String getOwner() {
        return owner;
    }

    public void setOwner(String owner) {
        this.owner = owner;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getApplicationScenario() {
        return applicationScenario;
    }

    public void setApplicationScenario(String applicationScenario) {
        this.applicationScenario = applicationScenario;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getEntityType() {
        return entityType;
    }

    public void setEntityType(String entityType) {
        this.entityType = entityType;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public void setIdempotencyKey(String idempotencyKey) {
        this.idempotencyKey = idempotencyKey;
    }

    public Map<String, Object> getAdditionalAttributes() {
        return additionalAttributes;
    }

    public void setAdditionalAttributes(Map<String, Object> additionalAttributes) {
        this.additionalAttributes = additionalAttributes == null
                ? new LinkedHashMap<String, Object>()
                : new LinkedHashMap<String, Object>(additionalAttributes);
    }
}
