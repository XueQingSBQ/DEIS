package com.dris.sdk.next.application.support;

import com.dris.sdk.next.exception.ValidationException;
import com.dris.sdk.next.request.RegisterIdentifierRequest;
import com.dris.sdk.next.repository.RegistrationDictionary;
import com.dris.sdk.next.rule.IdentifierRules;

public class RegistrationRequestValidator {
    public void validate(RegisterIdentifierRequest request, RegistrationDictionary dictionary) {
        if (request == null) {
            throw new ValidationException("register request is required");
        }
        if (dictionary == null) {
            throw new ValidationException("registrationDictionary is required");
        }

        IdentifierRules.validateRegionCode(request.getRegionCode());
        IdentifierRules.validateOrganizationCode(request.getOrganizationCode());
        requireText(request.getIndustry(), "industry is required");
        IdentifierRules.resolveDataCategoryCode(request.getDataCategory());
        IdentifierRules.resolveRegistrationTypeCode(request.getRegistrationType());
        requireText(request.getOwner(), "owner is required");

        if (!dictionary.existsRegionCode(request.getRegionCode())) {
            throw new ValidationException("regionCode does not exist");
        }
        if (!dictionary.existsOrganizationCode(request.getOrganizationCode())) {
            throw new ValidationException("organizationCode does not exist");
        }
        if (!dictionary.existsIndustry(request.getIndustry())) {
            throw new ValidationException("industry does not exist");
        }
        if (request.getOwner().length() > 100) {
            throw new ValidationException("owner is too long");
        }
        if (request.getDescription() != null && request.getDescription().length() > 500) {
            throw new ValidationException("description is too long");
        }
        if (request.getApplicationScenario() != null && request.getApplicationScenario().length() > 200) {
            throw new ValidationException("applicationScenario is too long");
        }
    }

    private void requireText(String value, String message) {
        if (value == null || value.trim().isEmpty()) {
            throw new ValidationException(message);
        }
    }
}
