package com.dris.v2.backend.sdknext;

import com.dris.v2.backend.config.BackendV2Properties;
import com.dris.sdk.next.domain.IdentifierCode;
import com.dris.sdk.next.exception.ValidationException;
import com.dris.sdk.next.repository.RegistrationDictionary;
import com.dris.sdk.next.rule.IdentifierRules;
import org.springframework.stereotype.Component;

@Component
public class LocalIdentifierPolicyValidator {
    private final BackendV2Properties properties;
    private final RegistrationDictionary registrationDictionary;

    public LocalIdentifierPolicyValidator(BackendV2Properties properties, RegistrationDictionary registrationDictionary) {
        this.properties = properties;
        this.registrationDictionary = registrationDictionary;
    }

    public void validateRegisterTarget(String regionCode, String organizationCode) {
        if (!safe(properties.getRegionCode()).equals(safe(regionCode))) {
            throw new ValidationException("regionCode does not match system configuration");
        }
        if (!safe(properties.getOrganizationCode()).equals(safe(organizationCode))) {
            throw new ValidationException("organizationCode does not match system configuration");
        }
    }

    public IdentifierCode parseAndValidateLocalIdentifier(String identifier) {
        IdentifierCode code = IdentifierRules.parse(identifier);
        if (!safe(properties.getRegionCode()).equals(code.getRegionCode())) {
            throw new ValidationException("identifier region is invalid");
        }
        if (!safe(properties.getOrganizationCode()).equals(code.getOrganizationCode())) {
            throw new ValidationException("identifier organization is invalid");
        }
        if (properties.isStrictIndustryValidation() && !registrationDictionary.existsIndustryCode(code.getIndustryCode())) {
            throw new ValidationException("identifier industry code is invalid");
        }
        return code;
    }

    public boolean isValidForLocalNode(String identifier) {
        try {
            parseAndValidateLocalIdentifier(identifier);
            return true;
        } catch (RuntimeException exception) {
            return false;
        }
    }

    private String safe(String value) {
        return value == null ? "" : value.trim();
    }
}
