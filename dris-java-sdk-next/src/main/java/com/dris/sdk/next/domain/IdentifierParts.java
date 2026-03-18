package com.dris.sdk.next.domain;

@Deprecated
public class IdentifierParts extends IdentifierCode {
    public IdentifierParts(String regionCode, String organizationCode, String industryCode, String dataCategoryCode,
                           String registrationTypeCode, String date, String credential) {
        super(regionCode, organizationCode, industryCode, dataCategoryCode, registrationTypeCode, date, credential);
    }
}