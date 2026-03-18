package com.dris.v2.backend.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.URL;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class BackendDbBusinessIntegrationTest {
    private static final String INDUSTRY_AGRI = "\u519C\u4E1A";
    private static final String CATEGORY_PERSONAL = "\u4E2A\u4EBA\u6570\u636E";
    private static final String REG_TYPE_PRODUCT_SERVICE = "\u6570\u636E\u4EA7\u54C1/\u670D\u52A1";

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void invalidIdentifierRule_shouldBeRejectedBySdkValidation() throws Exception {
        mockMvc.perform(get("/api/v2/identifiers/id-1/validate"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ok").value(true))
                .andExpect(jsonPath("$.data").value(false));

        mockMvc.perform(get("/api/v2/identifiers/id-1/exists"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ok").value(true))
                .andExpect(jsonPath("$.data").value(false));

        mockMvc.perform(get("/api/v2/identifiers/id-1/metadata"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.ok").value(false))
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void chineseBoundaryValidation_shouldWorkAndBlockInvalidInputs() throws Exception {
        String id1 = registerIdentifier("boundary_case_a");
        assertEquals(30, id1.length());

        Map<String, Object> invalidCategoryVariant = baseRegistrationRequest(
                "boundary_invalid_category_variant",
                INDUSTRY_AGRI,
                "\u4E2A\u4EBA \u6570\u636E",
                REG_TYPE_PRODUCT_SERVICE
        );
        mockMvc.perform(post("/api/v2/identifiers/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidCategoryVariant)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.ok").value(false))
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

        Map<String, Object> invalidRegTypeVariantNoSlash = baseRegistrationRequest(
                "boundary_invalid_regtype_noslash",
                INDUSTRY_AGRI,
                CATEGORY_PERSONAL,
                "\u6570\u636E\u4EA7\u54C1\u670D\u52A1"
        );
        mockMvc.perform(post("/api/v2/identifiers/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidRegTypeVariantNoSlash)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.ok").value(false))
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

        Map<String, Object> invalidRegTypeVariantFullWidth = baseRegistrationRequest(
                "boundary_invalid_regtype_fullwidth",
                INDUSTRY_AGRI,
                CATEGORY_PERSONAL,
                "\u6570\u636E\u4EA7\u54C1\uFF0F\u670D\u52A1"
        );
        mockMvc.perform(post("/api/v2/identifiers/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidRegTypeVariantFullWidth)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.ok").value(false))
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

        Map<String, Object> invalidIndustry = baseRegistrationRequest(
                "boundary_invalid_industry",
                "\u4E0D\u5B58\u5728\u884C\u4E1A",
                CATEGORY_PERSONAL,
                REG_TYPE_PRODUCT_SERVICE
        );
        mockMvc.perform(post("/api/v2/identifiers/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidIndustry)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.ok").value(false))
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

        Map<String, Object> invalidCategory = baseRegistrationRequest(
                "boundary_invalid_category",
                INDUSTRY_AGRI,
                "\u79C1\u6709\u6570\u636E",
                REG_TYPE_PRODUCT_SERVICE
        );
        mockMvc.perform(post("/api/v2/identifiers/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidCategory)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.ok").value(false))
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

        Map<String, Object> invalidRegistrationType = baseRegistrationRequest(
                "boundary_invalid_reg_type",
                INDUSTRY_AGRI,
                CATEGORY_PERSONAL,
                "\u6570\u636E\u670D\u52A1\u4EA7\u54C1"
        );
        mockMvc.perform(post("/api/v2/identifiers/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidRegistrationType)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.ok").value(false))
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

        mockMvc.perform(delete("/api/v2/identifiers/" + id1)).andExpect(status().isOk());
    }

    @Test
    void registrationFieldLengthBoundary_shouldPassAtMaxAndRejectOverLimit() throws Exception {
        Map<String, Object> maxBoundary = baseRegistrationRequest(
                "boundary_length_max",
                INDUSTRY_AGRI,
                CATEGORY_PERSONAL,
                REG_TYPE_PRODUCT_SERVICE
        );
        maxBoundary.put("owner", repeat('o', 100));
        maxBoundary.put("description", repeat('d', 500));
        maxBoundary.put("applicationScenario", repeat('s', 200));
        String maxId = registerIdentifier(maxBoundary);
        assertEquals(30, maxId.length());

        Map<String, Object> ownerTooLong = baseRegistrationRequest(
                "boundary_owner_too_long",
                INDUSTRY_AGRI,
                CATEGORY_PERSONAL,
                REG_TYPE_PRODUCT_SERVICE
        );
        ownerTooLong.put("owner", repeat('o', 101));
        mockMvc.perform(post("/api/v2/identifiers/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(ownerTooLong)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.ok").value(false))
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

        Map<String, Object> descriptionTooLong = baseRegistrationRequest(
                "boundary_description_too_long",
                INDUSTRY_AGRI,
                CATEGORY_PERSONAL,
                REG_TYPE_PRODUCT_SERVICE
        );
        descriptionTooLong.put("description", repeat('d', 501));
        mockMvc.perform(post("/api/v2/identifiers/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(descriptionTooLong)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.ok").value(false))
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

        Map<String, Object> scenarioTooLong = baseRegistrationRequest(
                "boundary_scenario_too_long",
                INDUSTRY_AGRI,
                CATEGORY_PERSONAL,
                REG_TYPE_PRODUCT_SERVICE
        );
        scenarioTooLong.put("applicationScenario", repeat('s', 201));
        mockMvc.perform(post("/api/v2/identifiers/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(scenarioTooLong)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.ok").value(false))
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

        mockMvc.perform(delete("/api/v2/identifiers/" + maxId)).andExpect(status().isOk());
    }

    @Test
    void registerIdempotency_shouldReturnSameIdentifierForSameKey() throws Exception {
        Map<String, Object> req1 = baseRegistrationRequest(
                "boundary_idempotency_a",
                INDUSTRY_AGRI,
                CATEGORY_PERSONAL,
                REG_TYPE_PRODUCT_SERVICE
        );
        Map<String, Object> attrs1 = new LinkedHashMap<String, Object>();
        attrs1.put("_idempotencyKey", "api-idem-001");
        req1.put("additionalAttributes", attrs1);
        String id1 = registerIdentifier(req1);

        Map<String, Object> req2 = baseRegistrationRequest(
                "boundary_idempotency_a_repeat",
                INDUSTRY_AGRI,
                CATEGORY_PERSONAL,
                REG_TYPE_PRODUCT_SERVICE
        );
        Map<String, Object> attrs2 = new LinkedHashMap<String, Object>();
        attrs2.put("_idempotencyKey", "api-idem-001");
        req2.put("additionalAttributes", attrs2);
        String id2 = registerIdentifier(req2);
        assertEquals(id1, id2);

        Map<String, Object> req3 = baseRegistrationRequest(
                "boundary_idempotency_b",
                INDUSTRY_AGRI,
                CATEGORY_PERSONAL,
                REG_TYPE_PRODUCT_SERVICE
        );
        Map<String, Object> attrs3 = new LinkedHashMap<String, Object>();
        attrs3.put("_idempotencyKey", "api-idem-002");
        req3.put("additionalAttributes", attrs3);
        String id3 = registerIdentifier(req3);
        assertNotEquals(id1, id3);

        mockMvc.perform(delete("/api/v2/identifiers/" + id1)).andExpect(status().isOk());
        mockMvc.perform(delete("/api/v2/identifiers/" + id3)).andExpect(status().isOk());
    }

    @Test
    void fullUpdateShouldRejectInvalidClassificationValues() throws Exception {
        String identifier = registerIdentifier("boundary_full_update_invalid_classification");

        MvcResult metadataResult = mockMvc.perform(get("/api/v2/identifiers/" + identifier + "/metadata"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ok").value(true))
                .andReturn();
        JsonNode metadata = readDataNode(metadataResult);

        ObjectNode badCategory = (ObjectNode) metadata.deepCopy();
        badCategory.put("dataCategory", "\u4E2A\u4EBA \u6570\u636E");
        mockMvc.perform(put("/api/v2/identifiers/" + identifier + "/metadata")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(badCategory)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.ok").value(false))
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

        ObjectNode badRegType = (ObjectNode) metadata.deepCopy();
        badRegType.put("registrationType", "\u6570\u636E\u4EA7\u54C1\u670D\u52A1");
        mockMvc.perform(put("/api/v2/identifiers/" + identifier + "/metadata")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(badRegType)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.ok").value(false))
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

        ObjectNode badIndustry = (ObjectNode) metadata.deepCopy();
        badIndustry.put("industry", "\u4E0D\u5B58\u5728\u884C\u4E1A");
        mockMvc.perform(put("/api/v2/identifiers/" + identifier + "/metadata")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(badIndustry)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.ok").value(false))
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

        mockMvc.perform(delete("/api/v2/identifiers/" + identifier)).andExpect(status().isOk());
    }

    @Test
    void currentBackendBusinessApis_shouldWorkOnMysql() throws Exception {
        String identifier = registerIdentifier("road_flow_realtime");
        assertEquals(30, identifier.length());

        mockMvc.perform(get("/api/v2/identifiers/" + identifier + "/validate"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ok").value(true))
                .andExpect(jsonPath("$.data").value(true));

        mockMvc.perform(get("/api/v2/identifiers/" + identifier + "/exists"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ok").value(true))
                .andExpect(jsonPath("$.data").value(true));

        MvcResult metadataResult = mockMvc.perform(get("/api/v2/identifiers/" + identifier + "/metadata"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ok").value(true))
                .andReturn();
        JsonNode metadata = readDataNode(metadataResult);
        assertEquals(identifier, metadata.path("identifier").asText());

        mockMvc.perform(get("/api/v2/identifiers/" + identifier + "/object"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ok").value(true))
                .andExpect(jsonPath("$.data.identifier").value(identifier));

        List<String> batchIdentifiers = registerBatch();
        assertEquals(2, batchIdentifiers.size());
        assertNotNull(batchIdentifiers.get(0));
        assertNotNull(batchIdentifiers.get(1));

        mockMvc.perform(post("/api/v2/identifiers/resolve/batch")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Arrays.asList(identifier, batchIdentifiers.get(0)))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ok").value(true));

        ObjectNode updateBody = (ObjectNode) metadata.deepCopy();
        updateBody.put("name", "road_flow_realtime_v2");
        updateBody.put("description", "full_update_description");
        updateBody.put("updateTime", "2026-03-05T20:00:00Z");
        mockMvc.perform(put("/api/v2/identifiers/" + identifier + "/metadata")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateBody)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ok").value(true))
                .andExpect(jsonPath("$.data").value(true));

        Map<String, String> patchBody = new LinkedHashMap<String, String>();
        patchBody.put("description", "partial_update_description");
        mockMvc.perform(patch("/api/v2/identifiers/" + identifier + "/metadata")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(patchBody)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ok").value(true))
                .andExpect(jsonPath("$.data").value(true));

        Map<String, Object> component = new LinkedHashMap<String, Object>();
        component.put("componentType", "resource");
        component.put("storageType", "external_uri");
        component.put("accessMethod", "download");
        component.put("contentType", "application/json");
        component.put("format", "json");
        component.put("locator", "https://example.com/data/traffic/" + identifier + ".json");
        component.put("size", 2048);
        component.put("hashValue", "sha256:abcd1234");
        component.put("etag", "etag-1");
        component.put("versionId", "v1");
        component.put("createdAt", "2026-03-05T12:30:00Z");

        mockMvc.perform(put("/api/v2/identifiers/" + identifier + "/component")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(component)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ok").value(true))
                .andExpect(jsonPath("$.data").value(true));

        mockMvc.perform(get("/api/v2/identifiers/" + identifier + "/component"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ok").value(true))
                .andExpect(jsonPath("$.data.accessMethod").value("download"));

        mockMvc.perform(delete("/api/v2/identifiers/" + identifier + "/component"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ok").value(true))
                .andExpect(jsonPath("$.data").value(true));

        Map<String, Object> search = new LinkedHashMap<String, Object>();
        search.put("naturalQuery", "traffic flow camera");
        search.put("limit", 20);
        search.put("offset", 0);
        search.put("mode", "HYBRID");
        mockMvc.perform(post("/api/v2/search")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(search)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ok").value(true));

        Map<String, Object> createUpload = new LinkedHashMap<String, Object>();
        createUpload.put("profile", "data_json");
        createUpload.put("filename", "flow_20260305.json");
        createUpload.put("size", 1024);
        createUpload.put("sha256", "f2a5d9");
        MvcResult uploadResult = mockMvc.perform(post("/api/v2/identifiers/" + identifier + "/upload-sessions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createUpload)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ok").value(true))
                .andReturn();
        String uploadId = readDataNode(uploadResult).path("uploadId").asText();
        assertFalse(uploadId.isEmpty());

        mockMvc.perform(post("/api/v2/upload-sessions/" + uploadId + "/complete"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.ok").value(false))
                .andExpect(jsonPath("$.code").exists());

        Map<String, Object> directResource = new LinkedHashMap<String, Object>();
        directResource.put("componentType", "resource");
        directResource.put("storageType", "external_uri");
        directResource.put("accessMethod", "download");
        directResource.put("contentType", "application/json");
        directResource.put("format", "json");
        directResource.put("locator", "https://example.com/data/flow_20260305.json");
        directResource.put("size", 1024);
        directResource.put("hashValue", "sha256:f2a5d9");
        directResource.put("etag", "etag-direct-1");
        directResource.put("versionId", "");
        directResource.put("createdAt", "2026-03-09T00:00:00Z");
        mockMvc.perform(put("/api/v2/identifiers/" + identifier + "/component")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(directResource)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ok").value(true));

        Map<String, Object> presign = new LinkedHashMap<String, Object>();
        presign.put("identifier", identifier);
        presign.put("expiresInSeconds", 600);
        mockMvc.perform(post("/api/v2/downloads/presign")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(presign)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ok").value(true));

        mockMvc.perform(post("/api/v2/ops/vector/sync"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ok").value(true));

        mockMvc.perform(get("/api/v2/ops/metrics"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ok").value(true));

        mockMvc.perform(delete("/api/v2/identifiers/" + identifier))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ok").value(true))
                .andExpect(jsonPath("$.data").value(true));

        MvcResult existsAfterDelete = mockMvc.perform(get("/api/v2/identifiers/" + identifier + "/exists"))
                .andExpect(status().isOk())
                .andReturn();
        assertFalse(readDataNode(existsAfterDelete).asBoolean());

        for (String batchId : batchIdentifiers) {
            mockMvc.perform(delete("/api/v2/identifiers/" + batchId))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.ok").value(true));
        }
    }

    @Test
    void uploadProfiles_resource_tool_service_shouldWorkOnCos() throws Exception {
        String resourceId = registerIdentifier("upload_cos_resource");
        Map<String, Object> resourceUpload = new LinkedHashMap<String, Object>();
        resourceUpload.put("profile", "data_json");
        resourceUpload.put("filename", "resource.json");
        resourceUpload.put("size", 128);
        resourceUpload.put("sha256", "abc123");
        MvcResult resourceUploadResult = mockMvc.perform(post("/api/v2/identifiers/" + resourceId + "/upload-sessions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(resourceUpload)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ok").value(true))
                .andExpect(jsonPath("$.data.put.url").value(org.hamcrest.Matchers.containsString("dris-repo-prod-1330585503.cos.ap-beijing.myqcloud.com")))
                .andReturn();
        String resourceUploadId = readDataNode(resourceUploadResult).path("uploadId").asText();
        mockMvc.perform(post("/api/v2/upload-sessions/" + resourceUploadId + "/complete"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.ok").value(false))
                .andExpect(jsonPath("$.code").exists());

        String toolId = registerIdentifier("upload_cos_tool");
        Map<String, Object> toolUpload = new LinkedHashMap<String, Object>();
        toolUpload.put("profile", "tool_zip_cli");
        toolUpload.put("filename", "tool.zip");
        toolUpload.put("size", 256);
        toolUpload.put("sha256", "toolhash123");
        MvcResult toolUploadResult = mockMvc.perform(post("/api/v2/identifiers/" + toolId + "/upload-sessions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(toolUpload)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ok").value(true))
                .andExpect(jsonPath("$.data.put.url").value(org.hamcrest.Matchers.containsString("dris-repo-prod-1330585503.cos.ap-beijing.myqcloud.com")))
                .andReturn();
        String toolUploadId = readDataNode(toolUploadResult).path("uploadId").asText();
        mockMvc.perform(post("/api/v2/upload-sessions/" + toolUploadId + "/complete"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.ok").value(false))
                .andExpect(jsonPath("$.code").exists());

        String serviceId = registerIdentifier("upload_cos_service");
        Map<String, Object> serviceUpload = new LinkedHashMap<String, Object>();
        serviceUpload.put("profile", "service_openapi_json");
        serviceUpload.put("filename", "openapi.json");
        serviceUpload.put("size", 0);
        serviceUpload.put("serviceEndpoint", "https://api.example.com/v1/invoke");
        mockMvc.perform(post("/api/v2/identifiers/" + serviceId + "/upload-sessions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(serviceUpload)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.ok").value(false))
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

        Map<String, Object> directService = new LinkedHashMap<String, Object>();
        directService.put("componentType", "service");
        directService.put("storageType", "api_service");
        directService.put("accessMethod", "api_call");
        directService.put("contentType", "service");
        directService.put("format", "rest");
        directService.put("locator", "https://api.example.com/v1/invoke");
        directService.put("size", 0);
        directService.put("hashValue", "");
        mockMvc.perform(put("/api/v2/identifiers/" + serviceId + "/component")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(directService)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ok").value(true));

        mockMvc.perform(get("/api/v2/identifiers/" + serviceId + "/component"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ok").value(true))
                .andExpect(jsonPath("$.data.componentType").value("service"))
                .andExpect(jsonPath("$.data.storageType").value("api_service"))
                .andExpect(jsonPath("$.data.accessMethod").value("api_call"))
                .andExpect(jsonPath("$.data.contentType").value("service"))
                .andExpect(jsonPath("$.data.locator").value("https://api.example.com/v1/invoke"));

        Map<String, Object> invalidServiceUpload = new LinkedHashMap<String, Object>();
        invalidServiceUpload.put("profile", "service_openapi_json");
        invalidServiceUpload.put("filename", "openapi.json");
        invalidServiceUpload.put("size", 0);
        mockMvc.perform(post("/api/v2/identifiers/" + serviceId + "/upload-sessions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidServiceUpload)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.ok").value(false))
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

        mockMvc.perform(delete("/api/v2/identifiers/" + resourceId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ok").value(true));
        mockMvc.perform(delete("/api/v2/identifiers/" + toolId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ok").value(true));
        mockMvc.perform(delete("/api/v2/identifiers/" + serviceId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ok").value(true));
    }

    @Test
    void directComponentBinding_toolAndService_shouldWorkWithoutObjectStorageUpload() throws Exception {
        String toolId = registerIdentifier("direct_tool_local_bind");
        Map<String, Object> toolComponent = new LinkedHashMap<String, Object>();
        toolComponent.put("componentType", "tool");
        toolComponent.put("storageType", "local_exec");
        toolComponent.put("accessMethod", "invoke");
        toolComponent.put("contentType", "tool");
        toolComponent.put("format", "json");
        toolComponent.put("locator", "local://tools/sum-cli");
        toolComponent.put("size", 0);
        toolComponent.put("hashValue", "");
        Map<String, Object> toolExtra = new LinkedHashMap<String, Object>();
        toolExtra.put("operationName", "sum");
        toolExtra.put("operationDesc", "sum two input numbers");
        toolExtra.put("argTemplate", "{\"a\":${a},\"b\":${b}}");
        Map<String, Object> inputSchema = new LinkedHashMap<String, Object>();
        inputSchema.put("type", "object");
        inputSchema.put("required", Arrays.asList("a", "b"));
        toolExtra.put("input_schema", inputSchema);
        Map<String, Object> outputSchema = new LinkedHashMap<String, Object>();
        outputSchema.put("type", "number");
        toolExtra.put("output_schema", outputSchema);
        toolComponent.put("extra", toolExtra);

        mockMvc.perform(put("/api/v2/identifiers/" + toolId + "/component")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(toolComponent)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ok").value(true))
                .andExpect(jsonPath("$.data").value(true));

        mockMvc.perform(get("/api/v2/identifiers/" + toolId + "/component"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ok").value(true))
                .andExpect(jsonPath("$.data.componentType").value("tool"))
                .andExpect(jsonPath("$.data.storageType").value("local_exec"))
                .andExpect(jsonPath("$.data.accessMethod").value("invoke"))
                .andExpect(jsonPath("$.data.locator").value("local://tools/sum-cli"))
                .andExpect(jsonPath("$.data.extra.operationName").value("sum"));

        String serviceId = registerIdentifier("direct_service_url_bind");
        Map<String, Object> serviceComponent = new LinkedHashMap<String, Object>();
        serviceComponent.put("componentType", "service");
        serviceComponent.put("storageType", "api_service");
        serviceComponent.put("accessMethod", "api_call");
        serviceComponent.put("contentType", "service");
        serviceComponent.put("format", "openapi_json");
        serviceComponent.put("locator", "https://api.example.com/v1/sum");
        serviceComponent.put("size", 0);
        Map<String, Object> serviceExtra = new LinkedHashMap<String, Object>();
        serviceExtra.put("operationName", "sum");
        serviceExtra.put("operationDesc", "sum two input numbers via api");
        serviceExtra.put("method", "POST");
        serviceExtra.put("path", "/v1/sum");
        serviceExtra.put("timeoutMs", 3000);
        serviceComponent.put("extra", serviceExtra);

        mockMvc.perform(put("/api/v2/identifiers/" + serviceId + "/component")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(serviceComponent)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ok").value(true))
                .andExpect(jsonPath("$.data").value(true));

        mockMvc.perform(get("/api/v2/identifiers/" + serviceId + "/component"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ok").value(true))
                .andExpect(jsonPath("$.data.componentType").value("service"))
                .andExpect(jsonPath("$.data.storageType").value("api_service"))
                .andExpect(jsonPath("$.data.accessMethod").value("api_call"))
                .andExpect(jsonPath("$.data.locator").value("https://api.example.com/v1/sum"))
                .andExpect(jsonPath("$.data.extra.operationName").value("sum"));

        Map<String, Object> invalidServiceComponent = new LinkedHashMap<String, Object>(serviceComponent);
        invalidServiceComponent.put("locator", "grpc://service.local/sum");
        mockMvc.perform(put("/api/v2/identifiers/" + serviceId + "/component")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidServiceComponent)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.ok").value(false))
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

        mockMvc.perform(delete("/api/v2/identifiers/" + toolId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ok").value(true));
        mockMvc.perform(delete("/api/v2/identifiers/" + serviceId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ok").value(true));
    }

    @Test
    void resolveDigitalObject_thenFetchResourceInvokeServiceExecuteTool_shouldReturnResults() throws Exception {
        HttpServer localServer = HttpServer.create(new InetSocketAddress(0), 0);
        localServer.createContext("/resource/data.json", new FixedBodyHandler("{\"value\":42,\"source\":\"local-resource\"}"));
        localServer.createContext("/service/sum", new HttpHandler() {
            @Override
            public void handle(HttpExchange exchange) {
                try {
                    String query = exchange.getRequestURI().getRawQuery();
                    int a = queryParamAsInt(query, "a");
                    int b = queryParamAsInt(query, "b");
                    String body = "{\"result\":" + (a + b) + "}";
                    byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
                    exchange.getResponseHeaders().add("Content-Type", "application/json;charset=UTF-8");
                    exchange.sendResponseHeaders(200, bytes.length);
                    try (OutputStream os = exchange.getResponseBody()) {
                        os.write(bytes);
                    }
                } catch (Exception e) {
                    try {
                        byte[] bytes = "{\"error\":\"service_error\"}".getBytes(StandardCharsets.UTF_8);
                        exchange.sendResponseHeaders(500, bytes.length);
                        try (OutputStream os = exchange.getResponseBody()) {
                            os.write(bytes);
                        }
                    } catch (Exception ignore) {
                        // no-op
                    }
                } finally {
                    exchange.close();
                }
            }
        });
        localServer.start();
        int port = localServer.getAddress().getPort();
        String base = "http://127.0.0.1:" + port;

        String resourceId = registerIdentifier("resolve_exec_resource");
        String serviceId = registerIdentifier("resolve_exec_service");
        String toolId = registerIdentifier("resolve_exec_tool");

        try {
            Map<String, Object> resourceComponent = new LinkedHashMap<String, Object>();
            resourceComponent.put("componentType", "resource");
            resourceComponent.put("storageType", "external_uri");
            resourceComponent.put("accessMethod", "download");
            resourceComponent.put("contentType", "dataset");
            resourceComponent.put("format", "json");
            resourceComponent.put("locator", base + "/resource/data.json");
            resourceComponent.put("size", 36);
            resourceComponent.put("hashValue", "");
            mockMvc.perform(put("/api/v2/identifiers/" + resourceId + "/component")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(resourceComponent)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.ok").value(true));

            Map<String, Object> serviceComponent = new LinkedHashMap<String, Object>();
            serviceComponent.put("componentType", "service");
                serviceComponent.put("storageType", "api_service");
            serviceComponent.put("accessMethod", "api_call");
            serviceComponent.put("contentType", "service");
            serviceComponent.put("format", "openapi_json");
            serviceComponent.put("locator", base + "/service/sum");
            serviceComponent.put("size", 0);
            Map<String, Object> serviceExtra = new LinkedHashMap<String, Object>();
            serviceExtra.put("method", "GET");
            serviceExtra.put("operationName", "sum");
            serviceComponent.put("extra", serviceExtra);
            mockMvc.perform(put("/api/v2/identifiers/" + serviceId + "/component")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(serviceComponent)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.ok").value(true));

            Map<String, Object> toolComponent = new LinkedHashMap<String, Object>();
            toolComponent.put("componentType", "tool");
                toolComponent.put("storageType", "local_exec");
            toolComponent.put("accessMethod", "invoke");
            toolComponent.put("contentType", "tool");
            toolComponent.put("format", "json");
            toolComponent.put("locator", "local://tools/sum-cli");
            toolComponent.put("size", 0);
            toolComponent.put("hashValue", "");
            Map<String, Object> toolExtra = new LinkedHashMap<String, Object>();
            toolExtra.put("operationName", "sum");
            toolExtra.put("commandWindows", Arrays.asList("cmd", "/c", "set /a ${a}+${b}"));
            toolExtra.put("commandUnix", Arrays.asList("sh", "-lc", "expr ${a} + ${b}"));
            toolComponent.put("extra", toolExtra);
            mockMvc.perform(put("/api/v2/identifiers/" + toolId + "/component")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(toolComponent)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.ok").value(true));

            JsonNode resourceObject = readDataNode(mockMvc.perform(get("/api/v2/identifiers/" + resourceId + "/object"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.ok").value(true))
                    .andReturn());
            JsonNode serviceObject = readDataNode(mockMvc.perform(get("/api/v2/identifiers/" + serviceId + "/object"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.ok").value(true))
                    .andReturn());
            JsonNode toolObject = readDataNode(mockMvc.perform(get("/api/v2/identifiers/" + toolId + "/object"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.ok").value(true))
                    .andReturn());

            assertEquals(resourceId, resourceObject.path("metadata").path("identifier").asText());
            assertEquals(serviceId, serviceObject.path("metadata").path("identifier").asText());
            assertEquals(toolId, toolObject.path("metadata").path("identifier").asText());

            String resourceLocator = resourceObject.path("resource").path("locator").asText();
            String resourcePayload = httpGet(resourceLocator);
            assertTrue(resourcePayload.contains("\"value\":42"));

            String serviceLocator = serviceObject.path("resource").path("locator").asText();
            String servicePayload = httpGet(serviceLocator + "?a=7&b=5");
            assertTrue(servicePayload.contains("\"result\":12"));

            int toolResult = executeToolByResourceInfo(toolObject.path("resource"), 7, 5);
            assertEquals(12, toolResult);
        } finally {
            mockMvc.perform(delete("/api/v2/identifiers/" + resourceId))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.ok").value(true));
            mockMvc.perform(delete("/api/v2/identifiers/" + serviceId))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.ok").value(true));
            mockMvc.perform(delete("/api/v2/identifiers/" + toolId))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.ok").value(true));
            localServer.stop(0);
        }
    }

    @Test
    void toolExecuteApi_shouldReturnExecutionResultForFrontend() throws Exception {
        String toolId = registerIdentifier("tool_exec_api_demo");
        try {
            Map<String, Object> toolComponent = new LinkedHashMap<String, Object>();
            toolComponent.put("componentType", "tool");
                toolComponent.put("storageType", "local_exec");
            toolComponent.put("accessMethod", "invoke");
            toolComponent.put("contentType", "tool");
            toolComponent.put("format", "json");
            toolComponent.put("locator", "local://tools/sum-cli");
            toolComponent.put("size", 0);
            toolComponent.put("hashValue", "");
            Map<String, Object> toolExtra = new LinkedHashMap<String, Object>();
            toolExtra.put("operationName", "sum");
            toolExtra.put("commandWindows", Arrays.asList("cmd", "/c", "set /a ${a}+${b}"));
            toolExtra.put("commandUnix", Arrays.asList("sh", "-lc", "expr ${a} + ${b}"));
            toolComponent.put("extra", toolExtra);

            mockMvc.perform(put("/api/v2/identifiers/" + toolId + "/component")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(toolComponent)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.ok").value(true))
                    .andExpect(jsonPath("$.data").value(true));

            Map<String, Object> executeRequest = new LinkedHashMap<String, Object>();
            Map<String, Object> args = new LinkedHashMap<String, Object>();
            args.put("a", 7);
            args.put("b", 5);
            executeRequest.put("args", args);
            executeRequest.put("timeoutMs", 5000);

            mockMvc.perform(post("/api/v2/identifiers/" + toolId + "/tool/execute")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(executeRequest)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.ok").value(true))
                    .andExpect(jsonPath("$.data.identifier").value(toolId))
                    .andExpect(jsonPath("$.data.operationName").value("sum"))
                    .andExpect(jsonPath("$.data.success").value(true))
                    .andExpect(jsonPath("$.data.exitCode").value(0))
                    .andExpect(jsonPath("$.data.stdout").value(org.hamcrest.Matchers.containsString("12")));
        } finally {
            mockMvc.perform(delete("/api/v2/identifiers/" + toolId))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.ok").value(true));
        }
    }

    @Test
    void localSearch_shouldSupportComponentFiltersAndReturnSummary() throws Exception {
        String serviceId = registerIdentifier("search_local_service");
        String resourceId = registerIdentifier("search_local_resource");
        try {
            Map<String, Object> serviceComponent = new LinkedHashMap<String, Object>();
            serviceComponent.put("componentType", "service");
                serviceComponent.put("storageType", "api_service");
            serviceComponent.put("accessMethod", "api_call");
            serviceComponent.put("contentType", "service");
            serviceComponent.put("format", "openapi_json");
            serviceComponent.put("locator", "https://api.example.com/v1/sum");
            serviceComponent.put("size", 0);
            serviceComponent.put("hashValue", "");
            mockMvc.perform(put("/api/v2/identifiers/" + serviceId + "/component")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(serviceComponent)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.ok").value(true));

            Map<String, Object> resourceComponent = new LinkedHashMap<String, Object>();
            resourceComponent.put("componentType", "resource");
            resourceComponent.put("storageType", "external_uri");
            resourceComponent.put("accessMethod", "download");
            resourceComponent.put("contentType", "dataset");
            resourceComponent.put("format", "json");
            resourceComponent.put("locator", "https://example.com/data/" + resourceId + ".json");
            resourceComponent.put("size", 10);
            resourceComponent.put("hashValue", "");
            mockMvc.perform(put("/api/v2/identifiers/" + resourceId + "/component")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(resourceComponent)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.ok").value(true));

            Map<String, Object> search = new LinkedHashMap<String, Object>();
            search.put("naturalQuery", "sum service");
            search.put("fields", Arrays.asList("name", "description", "componentType", "contentType", "locator"));
            Map<String, String> filters = new LinkedHashMap<String, String>();
            filters.put("componentType", "service");
            filters.put("accessMethod", "api_call");
            search.put("filters", filters);
            search.put("limit", 10);
            search.put("offset", 0);
            search.put("mode", "HYBRID");

            JsonNode data = readDataNode(mockMvc.perform(post("/api/v2/search")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(search)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.ok").value(true))
                    .andReturn());

            assertTrue(data.isArray());
            assertTrue(data.size() > 0);
            JsonNode matched = null;
            for (JsonNode item : data) {
                if (serviceId.equals(item.path("identifier").asText())) {
                    matched = item;
                    break;
                }
            }
            assertNotNull(matched);
            assertEquals("service", matched.path("componentType").asText());
            assertEquals("api_service", matched.path("storageType").asText());
            assertEquals("api_call", matched.path("accessMethod").asText());
            assertEquals("service", matched.path("contentType").asText());
            assertEquals("openapi_json", matched.path("format").asText());
            assertEquals("local", matched.path("source").asText());
        } finally {
            mockMvc.perform(delete("/api/v2/identifiers/" + serviceId))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.ok").value(true));
            mockMvc.perform(delete("/api/v2/identifiers/" + resourceId))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.ok").value(true));
        }
    }

    @Test
    void vectorSemanticSearch_shouldRecallAfterSync() throws Exception {
        Map<String, Object> req = baseRegistrationRequest(
                "vector_semantic_service_unique",
                INDUSTRY_AGRI,
                CATEGORY_PERSONAL,
                REG_TYPE_PRODUCT_SERVICE
        );
        req.put("description", "闁告碍鍨块崳娲矗椤掆偓濞叉牗绋夐幘鑸垫殢 婵懓鍊搁幏浼村嫉瀹ュ懎顫?缂佸鍋撻柡鍫濐槼閻︽蓟yzvector");
        String serviceId = registerIdentifier(req);
        try {
            Map<String, Object> serviceComponent = new LinkedHashMap<String, Object>();
            serviceComponent.put("componentType", "service");
                serviceComponent.put("storageType", "api_service");
            serviceComponent.put("accessMethod", "api_call");
            serviceComponent.put("contentType", "service");
            serviceComponent.put("format", "openapi_json");
            serviceComponent.put("locator", "https://api.example.com/v1/vector-sum");
            serviceComponent.put("size", 0);
            serviceComponent.put("hashValue", "");
            mockMvc.perform(put("/api/v2/identifiers/" + serviceId + "/component")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(serviceComponent)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.ok").value(true));

            mockMvc.perform(post("/api/v2/ops/vector/sync"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.ok").value(true));

            Map<String, Object> search = new LinkedHashMap<String, Object>();
            search.put("naturalQuery", "xyzvector sum api service");
            Map<String, String> filters = new LinkedHashMap<String, String>();
            filters.put("componentType", "service");
            search.put("filters", filters);
            search.put("limit", 5);
            search.put("offset", 0);
            search.put("mode", "SEMANTIC");

            JsonNode data = readDataNode(mockMvc.perform(post("/api/v2/search")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(search)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.ok").value(true))
                    .andReturn());
            assertTrue(data.isArray());
            assertTrue(data.size() > 0);
            JsonNode first = data.get(0);
            assertEquals(serviceId, first.path("identifier").asText());
            assertEquals("local", first.path("source").asText());
            assertTrue(first.path("semanticScore").asDouble() > 0.0);
        } finally {
            mockMvc.perform(delete("/api/v2/identifiers/" + serviceId))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.ok").value(true));
        }
    }

    @Test
    void globalSearch_shouldRankExactOwnerMatchBeforeManyPartialMatches() throws Exception {
        List<String> cleanup = new ArrayList<String>();
        try {
            for (int i = 0; i < 16; i++) {
                Map<String, Object> filler = baseRegistrationRequest(
                        "global_partial_owner_" + i,
                        INDUSTRY_AGRI,
                        CATEGORY_PERSONAL,
                        REG_TYPE_PRODUCT_SERVICE
                );
                filler.put("owner", "owner-demo");
                filler.put("description", "partial owner filler " + i);
                cleanup.add(registerIdentifier(filler));
            }

            Map<String, Object> exact = baseRegistrationRequest(
                    "global_exact_owner_target",
                    INDUSTRY_AGRI,
                    CATEGORY_PERSONAL,
                    REG_TYPE_PRODUCT_SERVICE
            );
            exact.put("owner", "verify-owner");
            exact.put("description", "exact owner target");
            String exactId = registerIdentifier(exact);
            cleanup.add(exactId);

            Map<String, Object> search = new LinkedHashMap<String, Object>();
            search.put("naturalQuery", "verify owner");
            search.put("fields", Arrays.asList("owner"));
            search.put("limit", 3);
            search.put("offset", 0);
            search.put("mode", "KEYWORD");
            search.put("strategy", "RECURSIVE");

            JsonNode data = readDataNode(mockMvc.perform(post("/api/v2/search/global")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(search)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.ok").value(true))
                    .andReturn());
            JsonNode items = data.path("items");
            assertTrue(items.isArray());
            assertTrue(items.size() > 0);
            assertEquals(exactId, items.get(0).path("identifier").asText());
            assertEquals("1100ABCD", items.get(0).path("source").asText());
        } finally {
            for (String identifier : cleanup) {
                mockMvc.perform(delete("/api/v2/identifiers/" + identifier))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.ok").value(true));
            }
        }
    }
    private String registerIdentifier(String name) throws Exception {
        return registerIdentifier(name, INDUSTRY_AGRI, CATEGORY_PERSONAL, REG_TYPE_PRODUCT_SERVICE);
    }

    private String registerIdentifier(String name, String industry, String dataCategory, String registrationType) throws Exception {
        Map<String, Object> request = baseRegistrationRequest(name, industry, dataCategory, registrationType);
        return registerIdentifier(request);
    }

    private String registerIdentifier(Map<String, Object> request) throws Exception {
        MvcResult register = mockMvc.perform(post("/api/v2/identifiers/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ok").value(true))
                .andReturn();
        return objectMapper.readTree(register.getResponse().getContentAsString(StandardCharsets.UTF_8)).path("data").asText();
    }

    private List<String> registerBatch() throws Exception {
        Map<String, Object> request1 = baseRegistrationRequest(
                "batch_dataset_1",
                INDUSTRY_AGRI,
                CATEGORY_PERSONAL,
                REG_TYPE_PRODUCT_SERVICE
        );
        request1.put("description", "Batch dataset 1");

        Map<String, Object> request2 = baseRegistrationRequest(
                "batch_dataset_2",
                INDUSTRY_AGRI,
                CATEGORY_PERSONAL,
                REG_TYPE_PRODUCT_SERVICE
        );
        request2.put("description", "Batch dataset 2");

        MvcResult result = mockMvc.perform(post("/api/v2/identifiers/register/batch")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Arrays.asList(request1, request2))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ok").value(true))
                .andReturn();

        JsonNode data = readDataNode(result);
        return Arrays.asList(data.get(0).asText(), data.get(1).asText());
    }

    private Map<String, Object> baseRegistrationRequest(String name, String industry, String dataCategory, String registrationType) {
        Map<String, Object> request = new LinkedHashMap<String, Object>();
        request.put("regionCode", "1100");
        request.put("organizationCode", "ABCD");
        request.put("industry", industry);
        request.put("dataCategory", dataCategory);
        request.put("registrationType", registrationType);
        request.put("owner", "city-traffic-center");
        request.put("description", "Realtime road traffic flow dataset");
        request.put("applicationScenario", "traffic_dispatch");
        request.put("name", name);
        request.put("entityType", "dataset");
        return request;
    }

    private JsonNode readDataNode(MvcResult result) throws Exception {
        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString(StandardCharsets.UTF_8));
        assertTrue(body.path("ok").asBoolean());
        return body.path("data");
    }

    private String httpGet(String url) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
        connection.setRequestMethod("GET");
        connection.setConnectTimeout(3000);
        connection.setReadTimeout(3000);
        int code = connection.getResponseCode();
        assertEquals(200, code);
        try (InputStream in = connection.getInputStream()) {
            return readAll(in);
        } finally {
            connection.disconnect();
        }
    }

    private int executeToolByResourceInfo(JsonNode resourceNode, int a, int b) throws Exception {
        JsonNode extra = resourceNode.path("extra");
        String key = isWindows() ? "commandWindows" : "commandUnix";
        JsonNode commandNode = extra.path(key);
        assertTrue(commandNode.isArray() && commandNode.size() > 0);
        List<String> cmd = new ArrayList<String>();
        for (JsonNode token : commandNode) {
            String s = token.asText();
            s = s.replace("${a}", String.valueOf(a));
            s = s.replace("${b}", String.valueOf(b));
            cmd.add(s);
        }

        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.redirectErrorStream(true);
        Process p = pb.start();
        String output;
        try (InputStream in = p.getInputStream()) {
            output = readAll(in).trim();
        }
        int exit = p.waitFor();
        assertEquals(0, exit);
        Matcher matcher = Pattern.compile("-?\\d+").matcher(output);
        assertTrue(matcher.find());
        return Integer.parseInt(matcher.group());
    }

    private String readAll(InputStream in) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[1024];
        int n;
        while ((n = in.read(buf)) >= 0) {
            out.write(buf, 0, n);
        }
        return new String(out.toByteArray(), StandardCharsets.UTF_8);
    }

    private boolean isWindows() {
        String os = System.getProperty("os.name");
        return os != null && os.toLowerCase().contains("win");
    }

    private static int queryParamAsInt(String query, String key) {
        if (query == null || query.trim().isEmpty()) {
            return 0;
        }
        String[] parts = query.split("&");
        for (String part : parts) {
            String[] kv = part.split("=", 2);
            if (kv.length == 2 && key.equals(kv[0])) {
                try {
                    return Integer.parseInt(kv[1]);
                } catch (Exception ignore) {
                    return 0;
                }
            }
        }
        return 0;
    }

    private static class FixedBodyHandler implements HttpHandler {
        private final byte[] body;

        private FixedBodyHandler(String body) {
            this.body = body.getBytes(StandardCharsets.UTF_8);
        }

        @Override
        public void handle(HttpExchange exchange) {
            try {
                exchange.getResponseHeaders().add("Content-Type", "application/json;charset=UTF-8");
                exchange.sendResponseHeaders(200, body.length);
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(body);
                }
            } catch (Exception ignore) {
                // no-op
            } finally {
                exchange.close();
            }
        }
    }

    private String repeat(char c, int count) {
        char[] chars = new char[count];
        java.util.Arrays.fill(chars, c);
        return new String(chars);
    }
}
