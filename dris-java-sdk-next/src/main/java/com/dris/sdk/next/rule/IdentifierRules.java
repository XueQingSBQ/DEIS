package com.dris.sdk.next.rule;

import com.dris.sdk.next.domain.IdentifierCode;
import com.dris.sdk.next.domain.IdentifierParts;
import com.dris.sdk.next.enumtype.DataCategory;
import com.dris.sdk.next.enumtype.MetadataStatus;
import com.dris.sdk.next.enumtype.RegistrationType;
import com.dris.sdk.next.exception.ValidationException;
import com.dris.sdk.next.request.RegisterIdentifierRequest;
import com.dris.sdk.next.repository.RegistrationDictionary;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.regex.Pattern;

public final class IdentifierRules {
    private static final Pattern REGION_PATTERN = Pattern.compile("^[0-9]{4}$");
    private static final Pattern ORGANIZATION_PATTERN = Pattern.compile("^[A-HJ-NP-Z]{4}$");
    private static final Pattern INDUSTRY_CODE_PATTERN = Pattern.compile("^[0-9]{2}$");
    private static final Pattern DATE_PATTERN = Pattern.compile("^[0-9]{8}$");
    private static final Pattern CREDENTIAL_SEED_PATTERN = Pattern.compile("^[0-9A-HJ-NP-Z]{9}$");
    private static final Pattern CREDENTIAL_PATTERN = Pattern.compile("^[0-9A-HJ-NP-Z]{10}$");
    private static final Pattern IDENTIFIER_PATTERN = Pattern.compile("^[0-9]{4}[A-HJ-NP-Z]{4}[0-9]{2}[1239][123][0-9]{8}[0-9A-HJ-NP-Z]{10}$");
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd").withZone(ZoneOffset.UTC);

    private static final int REGION_LEN = 4;
    private static final int ORG_LEN = 4;
    private static final int INDUSTRY_LEN = 2;
    private static final int DATA_CATEGORY_LEN = 1;
    private static final int REGISTRATION_TYPE_LEN = 1;
    private static final int DATE_LEN = 8;
    private static final int CREDENTIAL_LEN = 10;
    private static final int TOTAL_LEN = REGION_LEN + ORG_LEN + INDUSTRY_LEN + DATA_CATEGORY_LEN + REGISTRATION_TYPE_LEN + DATE_LEN + CREDENTIAL_LEN;

    private IdentifierRules() {
    }

    public static void validateIdentifier(String identifier) {
        parse(identifier);
    }

    public static boolean isValid(String identifier, RegistrationDictionary dictionary) {
        try {
            IdentifierCode code = parse(identifier);
            if (dictionary == null) {
                return true;
            }
            if (!dictionary.existsRegionCode(code.getRegionCode())) {
                return false;
            }
            if (!dictionary.existsOrganizationCode(code.getOrganizationCode())) {
                return false;
            }
            return dictionary.existsIndustryCode(code.getIndustryCode());
        } catch (RuntimeException exception) {
            return false;
        }
    }

    public static IdentifierCode parse(String identifier) {
        if (identifier == null || identifier.trim().isEmpty()) {
            throw new ValidationException("identifier is required");
        }
        if (identifier.length() != TOTAL_LEN) {
            throw new ValidationException("identifier format is invalid, expected length=" + TOTAL_LEN);
        }
        if (!IDENTIFIER_PATTERN.matcher(identifier).matches()) {
            throw new ValidationException("identifier format is invalid");
        }

        int offset = 0;
        String region = identifier.substring(offset, offset + REGION_LEN);
        offset += REGION_LEN;
        String organization = identifier.substring(offset, offset + ORG_LEN);
        offset += ORG_LEN;
        String industry = identifier.substring(offset, offset + INDUSTRY_LEN);
        offset += INDUSTRY_LEN;
        String dataCategory = identifier.substring(offset, offset + DATA_CATEGORY_LEN);
        offset += DATA_CATEGORY_LEN;
        String registrationType = identifier.substring(offset, offset + REGISTRATION_TYPE_LEN);
        offset += REGISTRATION_TYPE_LEN;
        String date = identifier.substring(offset, offset + DATE_LEN);
        offset += DATE_LEN;
        String credential = identifier.substring(offset);

        if (!DATE_PATTERN.matcher(date).matches() || !isDateValid(date)) {
            throw new ValidationException("identifier date is invalid");
        }
        String checkSource = identifier.substring(0, TOTAL_LEN - 1);
        char expected = calculateCheckDigit(checkSource);
        if (expected != identifier.charAt(TOTAL_LEN - 1)) {
            throw new ValidationException("identifier check digit is invalid");
        }
        return new IdentifierCode(region, organization, industry, dataCategory, registrationType, date, credential);
    }

    public static IdentifierParts parseParts(String identifier) {
        IdentifierCode code = parse(identifier);
        return new IdentifierParts(
                code.getRegionCode(), code.getOrganizationCode(), code.getIndustryCode(),
                code.getDataCategoryCode(), code.getRegistrationTypeCode(), code.getDate(), code.getCredential()
        );
    }

    public static void validateRegionCode(String regionCode) {
        if (regionCode == null || !REGION_PATTERN.matcher(regionCode).matches()) {
            throw new ValidationException("regionCode must be 4 digits");
        }
    }

    public static void validateOrganizationCode(String organizationCode) {
        if (organizationCode == null || !ORGANIZATION_PATTERN.matcher(organizationCode).matches()) {
            throw new ValidationException("organizationCode must be 4 uppercase letters");
        }
    }

    public static String resolveIndustryCode(String industry, RegistrationDictionary dictionary) {
        if (industry == null || industry.trim().isEmpty()) {
            throw new ValidationException("industry is required");
        }
        if (dictionary == null) {
            throw new ValidationException("registrationDictionary is required");
        }
        String code = dictionary.resolveIndustryCode(industry);
        if (code == null || !INDUSTRY_CODE_PATTERN.matcher(code).matches()) {
            throw new ValidationException("industry code is not configured");
        }
        return code;
    }

    public static String resolveDataCategoryCode(String dataCategory) {
        DataCategory category = DataCategory.fromValue(dataCategory);
        if (category == null) {
            throw new ValidationException("invalid dataCategory");
        }
        return category.getCode();
    }

    public static String resolveRegistrationTypeCode(String registrationType) {
        RegistrationType type = RegistrationType.fromValue(registrationType);
        if (type == null) {
            throw new ValidationException("invalid registrationType");
        }
        return type.getCode();
    }

    public static String buildPrefix(RegisterIdentifierRequest request, RegistrationDictionary dictionary, Instant now) {
        return request.getRegionCode()
                + request.getOrganizationCode()
                + resolveIndustryCode(request.getIndustry(), dictionary)
                + resolveDataCategoryCode(request.getDataCategory())
                + resolveRegistrationTypeCode(request.getRegistrationType())
                + DATE_FORMATTER.format(now);
    }

    public static String composeIdentifier(String prefix, String credentialSeed) {
        if (prefix == null || prefix.length() != REGION_LEN + ORG_LEN + INDUSTRY_LEN + DATA_CATEGORY_LEN + REGISTRATION_TYPE_LEN + DATE_LEN) {
            throw new ValidationException("identifier prefix length is invalid");
        }
        if (credentialSeed == null || !CREDENTIAL_SEED_PATTERN.matcher(credentialSeed).matches()) {
            throw new ValidationException("credential seed must be 9 base32-like chars");
        }
        return prefix + credentialSeed + calculateCheckDigit(prefix + credentialSeed);
    }

    public static boolean isAllowedStatus(String status) {
        MetadataStatus resolved = MetadataStatus.fromValue(status);
        return resolved == MetadataStatus.ACTIVE || resolved == MetadataStatus.INACTIVE;
    }

    public static String normalizeStatus(String status, String fallback) {
        MetadataStatus resolved = MetadataStatus.fromValue(status);
        if (resolved != null) {
            return resolved.wireValue();
        }
        MetadataStatus fallbackStatus = MetadataStatus.fromValue(fallback);
        if (fallbackStatus != null) {
            return fallbackStatus.wireValue();
        }
        return MetadataStatus.ACTIVE.wireValue();
    }

    public static char calculateCheckDigit(String code) {
        int sum = 0;
        for (int i = 0; i < code.length(); i++) {
            int val = charToNumber(code.charAt(i));
            sum += val * (i + 1);
        }
        int mod = sum % 34;
        if (mod <= 9) {
            return (char) ('0' + mod);
        }
        if (mod <= 17) {
            return (char) ('A' + (mod - 10));
        }
        if (mod <= 22) {
            return (char) ('J' + (mod - 18));
        }
        return (char) ('P' + (mod - 23));
    }

    private static int charToNumber(char c) {
        if (c >= '0' && c <= '9') {
            return c - '0';
        }
        if (c >= 'A' && c <= 'H') {
            return 10 + (c - 'A');
        }
        if (c >= 'J' && c <= 'N') {
            return 18 + (c - 'J');
        }
        if (c >= 'P' && c <= 'Z') {
            return 23 + (c - 'P');
        }
        return 0;
    }

    private static boolean isDateValid(String date) {
        try {
            LocalDate.parse(date, DateTimeFormatter.BASIC_ISO_DATE);
            int year = Integer.parseInt(date.substring(0, 4));
            return year >= 2000 && year <= 2099;
        } catch (Exception exception) {
            return false;
        }
    }

    public static String normalizeText(String value) {
        if (value == null) {
            return "";
        }
        return value.trim().toLowerCase(Locale.ROOT)
                .replace("_", "").replace("-", "").replace("/", "")
                .replace("\uFF0F", "").replace("\u3000", "")
                .replaceAll("\\s+", "");
    }
}
