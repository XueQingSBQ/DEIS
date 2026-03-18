package com.dris.sdk.next.adapter.memory;

import com.dris.sdk.next.repository.RegistrationDictionary;

import java.util.LinkedHashMap;
import java.util.Map;

public class SimpleRegistrationDictionary implements RegistrationDictionary {
    private final Map<String, String> regionCodes = new LinkedHashMap<String, String>();
    private final Map<String, String> organizationCodes = new LinkedHashMap<String, String>();
    private final Map<String, String> industryCodes = new LinkedHashMap<String, String>();

    public SimpleRegistrationDictionary addRegion(String regionCode) {
        regionCodes.put(regionCode, regionCode);
        return this;
    }

    public SimpleRegistrationDictionary addOrganization(String organizationCode) {
        organizationCodes.put(organizationCode, organizationCode);
        return this;
    }

    public SimpleRegistrationDictionary addIndustry(String industry, String code) {
        industryCodes.put(industry, code);
        return this;
    }

    @Override
    public boolean existsRegionCode(String regionCode) {
        return regionCodes.containsKey(regionCode);
    }

    @Override
    public boolean existsOrganizationCode(String organizationCode) {
        return organizationCodes.containsKey(organizationCode);
    }

    @Override
    public boolean existsIndustry(String industry) {
        return industryCodes.containsKey(industry);
    }

    @Override
    public boolean existsIndustryCode(String industryCode) {
        return industryCodes.containsValue(industryCode);
    }

    @Override
    public String resolveIndustryCode(String industry) {
        return industryCodes.get(industry);
    }
}