package com.dris.sdk.next.domain;

import java.util.Objects;
import java.util.regex.Pattern;

public class IdentifierCode {
    private static final Pattern REGION_CODE = Pattern.compile("^[0-9]{4}$");
    private static final Pattern ORG_CODE = Pattern.compile("^[A-HJ-NP-Z]{4}$");
    private static final Pattern INDUSTRY_CODE = Pattern.compile("^[0-9]{2}$");
    private static final Pattern DATA_CATEGORY_CODE = Pattern.compile("^[1239]{1}$");
    private static final Pattern REGISTRATION_TYPE_CODE = Pattern.compile("^[123]{1}$");
    private static final Pattern DATE = Pattern.compile("^[0-9]{8}$");
    private static final Pattern CREDENTIAL = Pattern.compile("^[0-9A-HJ-NP-Z]{10}$");

    private final String regionCode;
    private final String organizationCode;
    private final String industryCode;
    private final String dataCategoryCode;
    private final String registrationTypeCode;
    private final String date;
    private final String credential;

    public IdentifierCode(String regionCode, String organizationCode, String industryCode, String dataCategoryCode,
                          String registrationTypeCode, String date, String credential) {
        this.regionCode = Objects.requireNonNull(regionCode, "regionCode");
        this.organizationCode = Objects.requireNonNull(organizationCode, "organizationCode");
        this.industryCode = Objects.requireNonNull(industryCode, "industryCode");
        this.dataCategoryCode = Objects.requireNonNull(dataCategoryCode, "dataCategoryCode");
        this.registrationTypeCode = Objects.requireNonNull(registrationTypeCode, "registrationTypeCode");
        this.date = Objects.requireNonNull(date, "date");
        this.credential = Objects.requireNonNull(credential, "credential");
        validate();
    }

    protected void validate() {
        if (!REGION_CODE.matcher(regionCode).matches()) {
            throw new IllegalArgumentException("regionCode must be 4 digits");
        }
        if (!ORG_CODE.matcher(organizationCode).matches()) {
            throw new IllegalArgumentException("organizationCode must be 4 uppercase letters");
        }
        if (!INDUSTRY_CODE.matcher(industryCode).matches()) {
            throw new IllegalArgumentException("industryCode must be 2 digits");
        }
        if (!DATA_CATEGORY_CODE.matcher(dataCategoryCode).matches()) {
            throw new IllegalArgumentException("dataCategoryCode is invalid");
        }
        if (!REGISTRATION_TYPE_CODE.matcher(registrationTypeCode).matches()) {
            throw new IllegalArgumentException("registrationTypeCode is invalid");
        }
        if (!DATE.matcher(date).matches()) {
            throw new IllegalArgumentException("date must be yyyyMMdd");
        }
        if (!CREDENTIAL.matcher(credential).matches()) {
            throw new IllegalArgumentException("credential must be 10 base32-like chars");
        }
    }

    public String compact() {
        return regionCode + organizationCode + industryCode + dataCategoryCode + registrationTypeCode + date + credential;
    }

    public String getRegionCode() { return regionCode; }
    public String getOrganizationCode() { return organizationCode; }
    public String getIndustryCode() { return industryCode; }
    public String getDataCategoryCode() { return dataCategoryCode; }
    public String getRegistrationTypeCode() { return registrationTypeCode; }
    public String getDate() { return date; }
    public String getCredential() { return credential; }
}