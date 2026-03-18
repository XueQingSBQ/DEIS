package com.dris.v2.backend;

import com.dris.sdk.next.domain.IdentifierMetadata;
import com.dris.v2.backend.contract.registration.RegistrationRequest;
import com.dris.sdk.next.domain.Resource;
import com.dris.v2.backend.contract.search.SearchRequest;
import com.dris.v2.backend.contract.upload.CreateUploadSessionRequest;
import com.dris.v2.backend.contract.upload.DownloadPresignRequest;

import java.util.LinkedHashMap;
import java.util.Map;

public final class TestSamples {
    public static final String VALID_IDENTIFIER = "1100ABCD0211202603051234567895";
    public static final String VALID_IDENTIFIER_2 = "1100ABCD021120260305ABCDE1234E";
    public static final String VALID_IDENTIFIER_NOT_FOUND = "1100ABCD021120260305ZZZZZ99991";

    private TestSamples() {
    }

    public static RegistrationRequest registrationRequest() {
        RegistrationRequest request = new RegistrationRequest();
        request.setRegionCode("1100");
        request.setOrganizationCode("ABCD");
        request.setIndustry("鍐滀笟");
        request.setDataCategory("涓汉鏁版嵁");
        request.setRegistrationType("鏁版嵁浜у搧/鏈嶅姟");
        request.setOwner("city-traffic-center");
        request.setDescription("Realtime road traffic flow dataset");
        request.setApplicationScenario("traffic_dispatch");
        request.setName("road_flow_realtime");
        request.setEntityType("dataset");
        Map<String, Object> attrs = new LinkedHashMap<String, Object>();
        attrs.put("source", "iot-gateway-01");
        request.setAdditionalAttributes(attrs);
        return request;
    }

    public static IdentifierMetadata metadata() {
        IdentifierMetadata metadata = new IdentifierMetadata();
        metadata.setIdentifier(VALID_IDENTIFIER);
        metadata.setRegionCode("1100");
        metadata.setOrganizationCode("ABCD");
        metadata.setIndustry("鍐滀笟");
        metadata.setDataCategory("涓汉鏁版嵁");
        metadata.setRegistrationType("鏁版嵁浜у搧/鏈嶅姟");
        metadata.setDate("20260305");
        metadata.setOwner("city-traffic-center");
        metadata.setDescription("Realtime road traffic flow dataset");
        metadata.setApplicationScenario("traffic_dispatch");
        metadata.setName("road_flow_realtime");
        metadata.setEntityType("dataset");
        metadata.setStatus("active");
        metadata.setCreateTime("2026-03-05T12:00:00Z");
        metadata.setUpdateTime("2026-03-05T12:00:00Z");
        return metadata;
    }

    public static Map<String, String> partialFields() {
        Map<String, String> fields = new LinkedHashMap<String, String>();
        fields.put("name", "road_flow_realtime_v2");
        fields.put("description", "partial_update");
        fields.put("_expectedRevision", "3");
        return fields;
    }

    public static Resource resource() {
        Resource resource = new Resource();
        resource.setComponentType("resource");
        resource.setStorageType("external_uri");
        resource.setAccessMethod("download");
        resource.setContentType("application/json");
        resource.setFormat("json");
        resource.setLocator("https://example.com/data/traffic/20260305.json");
        resource.setSize(2048L);
        resource.setHashValue("sha256:abcd1234");
        resource.setEtag("etag-1");
        resource.setVersionId("v1");
        resource.setCreatedAt("2026-03-05T12:30:00Z");
        Map<String, Object> extra = new LinkedHashMap<String, Object>();
        extra.put("objectKey", "identifiers/" + VALID_IDENTIFIER + "/resource");
        resource.setExtra(extra);
        return resource;
    }

    public static SearchRequest searchRequest() {
        SearchRequest request = new SearchRequest();
        request.setNaturalQuery("traffic camera intersection");
        request.setLimit(20);
        request.setOffset(0);
        return request;
    }

    public static CreateUploadSessionRequest uploadSessionRequest() {
        CreateUploadSessionRequest request = new CreateUploadSessionRequest();
        request.setProfile("data_json");
        request.setFilename("flow_20260305.json");
        request.setSize(2048L);
        request.setSha256("f2a5d9");
        return request;
    }

    public static DownloadPresignRequest downloadPresignRequest() {
        DownloadPresignRequest request = new DownloadPresignRequest();
        request.setIdentifier(VALID_IDENTIFIER);
        request.setExpiresInSeconds(600);
        return request;
    }
}
