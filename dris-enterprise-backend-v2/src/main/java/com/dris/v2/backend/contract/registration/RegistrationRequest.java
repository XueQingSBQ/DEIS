package com.dris.v2.backend.contract.registration;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

public class RegistrationRequest {
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
    private Map<String, Object> additionalAttributes = new LinkedHashMap<>();

    public RegistrationRequest() {
    }

    public void validate() {
        if (regionCode == null || !regionCode.matches("^[0-9]{4}$")) {
            throw new IllegalArgumentException("regionCode must be 4 digits");
        }
        if (organizationCode == null || !organizationCode.matches("^[A-HJ-NP-Z0-9]{4}$")) {
            throw new IllegalArgumentException("organizationCode must be 4 alnum characters");
        }
        if (industry == null || industry.trim().isEmpty()) {
            throw new IllegalArgumentException("industry is required");
        }
        if (owner == null || owner.trim().isEmpty()) {
            throw new IllegalArgumentException("owner is required");
        }
    }

    public static RegistrationRequest of(
            String regionCode,
            String organizationCode,
            String industry,
            String dataCategory,
            String registrationType,
            String owner
    ) {
        RegistrationRequest request = new RegistrationRequest();
        request.regionCode = Objects.requireNonNull(regionCode, "regionCode");
        request.organizationCode = Objects.requireNonNull(organizationCode, "organizationCode");
        request.industry = Objects.requireNonNull(industry, "industry");
        request.dataCategory = dataCategory;
        request.registrationType = registrationType;
        request.owner = Objects.requireNonNull(owner, "owner");
        request.description = "";
        request.applicationScenario = "";
        request.name = "";
        request.entityType = "";
        request.validate();
        return request;
    }

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

    public Map<String, Object> getAdditionalAttributes() {
        return additionalAttributes;
    }

    public void setAdditionalAttributes(Map<String, Object> additionalAttributes) {
        this.additionalAttributes = additionalAttributes == null ? new LinkedHashMap<>() : additionalAttributes;
    }
}
