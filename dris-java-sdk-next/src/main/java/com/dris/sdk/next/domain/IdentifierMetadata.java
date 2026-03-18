package com.dris.sdk.next.domain;

import java.util.LinkedHashMap;
import java.util.Map;

public class IdentifierMetadata {
    private String identifier;
    private String regionCode;
    private String organizationCode;
    private String industry;
    private String dataCategory;
    private String registrationType;
    private String date;
    private String owner;
    private String description;
    private String applicationScenario;
    private String name;
    private String entityType;
    private String status;
    private String createTime;
    private String updateTime;
    private Map<String, Object> additionalAttributes = new LinkedHashMap<String, Object>();

    public void validateForUpdate() {
        if (identifier == null || identifier.trim().isEmpty()) {
            throw new IllegalArgumentException("identifier is required");
        }
    }

    public IdentifierMetadata copy() {
        IdentifierMetadata copy = new IdentifierMetadata();
        copy.identifier = identifier;
        copy.regionCode = regionCode;
        copy.organizationCode = organizationCode;
        copy.industry = industry;
        copy.dataCategory = dataCategory;
        copy.registrationType = registrationType;
        copy.date = date;
        copy.owner = owner;
        copy.description = description;
        copy.applicationScenario = applicationScenario;
        copy.name = name;
        copy.entityType = entityType;
        copy.status = status;
        copy.createTime = createTime;
        copy.updateTime = updateTime;
        copy.additionalAttributes = new LinkedHashMap<String, Object>(additionalAttributes);
        return copy;
    }

    public String getIdentifier() { return identifier; }
    public void setIdentifier(String identifier) { this.identifier = identifier; }
    public String getRegionCode() { return regionCode; }
    public void setRegionCode(String regionCode) { this.regionCode = regionCode; }
    public String getOrganizationCode() { return organizationCode; }
    public void setOrganizationCode(String organizationCode) { this.organizationCode = organizationCode; }
    public String getIndustry() { return industry; }
    public void setIndustry(String industry) { this.industry = industry; }
    public String getDataCategory() { return dataCategory; }
    public void setDataCategory(String dataCategory) { this.dataCategory = dataCategory; }
    public String getRegistrationType() { return registrationType; }
    public void setRegistrationType(String registrationType) { this.registrationType = registrationType; }
    public String getDate() { return date; }
    public void setDate(String date) { this.date = date; }
    public String getOwner() { return owner; }
    public void setOwner(String owner) { this.owner = owner; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getApplicationScenario() { return applicationScenario; }
    public void setApplicationScenario(String applicationScenario) { this.applicationScenario = applicationScenario; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getEntityType() { return entityType; }
    public void setEntityType(String entityType) { this.entityType = entityType; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getCreateTime() { return createTime; }
    public void setCreateTime(String createTime) { this.createTime = createTime; }
    public String getUpdateTime() { return updateTime; }
    public void setUpdateTime(String updateTime) { this.updateTime = updateTime; }
    public Map<String, Object> getAdditionalAttributes() { return additionalAttributes; }
    public void setAdditionalAttributes(Map<String, Object> additionalAttributes) {
        this.additionalAttributes = additionalAttributes == null
                ? new LinkedHashMap<String, Object>()
                : new LinkedHashMap<String, Object>(additionalAttributes);
    }
}