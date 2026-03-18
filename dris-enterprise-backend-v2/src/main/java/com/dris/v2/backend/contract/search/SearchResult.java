package com.dris.v2.backend.contract.search;

public class SearchResult {
    private String identifier;
    private String name;
    private String description;
    private String owner;
    private String industry;
    private String dataCategory;
    private String registrationType;
    private String entityType;
    private String updateTime;
    private String componentType;
    private String storageType;
    private String accessMethod;
    private String contentType;
    private String format;
    private String source = "local";
    private double keywordScore;
    private double semanticScore;
    private double localScore;
    private double fusionScore;
    private int displayRank;
    private double finalScore;

    public SearchResult() {
    }

    public String getIdentifier() {
        return identifier;
    }

    public void setIdentifier(String identifier) {
        this.identifier = identifier;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getOwner() {
        return owner;
    }

    public void setOwner(String owner) {
        this.owner = owner;
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

    public String getEntityType() {
        return entityType;
    }

    public void setEntityType(String entityType) {
        this.entityType = entityType;
    }

    public String getUpdateTime() {
        return updateTime;
    }

    public void setUpdateTime(String updateTime) {
        this.updateTime = updateTime;
    }

    public String getComponentType() {
        return componentType;
    }

    public void setComponentType(String componentType) {
        this.componentType = componentType;
    }

    public String getStorageType() {
        return storageType;
    }

    public void setStorageType(String storageType) {
        this.storageType = storageType;
    }

    public String getAccessMethod() {
        return accessMethod;
    }

    public void setAccessMethod(String accessMethod) {
        this.accessMethod = accessMethod;
    }

    public String getContentType() {
        return contentType;
    }

    public void setContentType(String contentType) {
        this.contentType = contentType;
    }

    public String getFormat() {
        return format;
    }

    public void setFormat(String format) {
        this.format = format;
    }

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }

    public double getKeywordScore() {
        return keywordScore;
    }

    public void setKeywordScore(double keywordScore) {
        this.keywordScore = keywordScore;
    }

    public double getSemanticScore() {
        return semanticScore;
    }

    public void setSemanticScore(double semanticScore) {
        this.semanticScore = semanticScore;
    }

    public double getLocalScore() {
        return localScore;
    }

    public void setLocalScore(double localScore) {
        this.localScore = localScore;
    }

    public double getFusionScore() {
        return fusionScore;
    }

    public void setFusionScore(double fusionScore) {
        this.fusionScore = fusionScore;
    }

    public int getDisplayRank() {
        return displayRank;
    }

    public void setDisplayRank(int displayRank) {
        this.displayRank = displayRank;
    }

    public double getFinalScore() {
        return finalScore;
    }

    public void setFinalScore(double finalScore) {
        this.finalScore = finalScore;
    }
}
