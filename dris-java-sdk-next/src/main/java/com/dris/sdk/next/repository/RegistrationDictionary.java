package com.dris.sdk.next.repository;

public interface RegistrationDictionary {
    boolean existsRegionCode(String regionCode);

    boolean existsOrganizationCode(String organizationCode);

    boolean existsIndustry(String industry);

    boolean existsIndustryCode(String industryCode);

    String resolveIndustryCode(String industry);
}