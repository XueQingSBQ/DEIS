package com.dris.v2.backend.control;

import com.dris.v2.backend.TestSamples;
import com.dris.v2.backend.model.BusinessException;
import com.dris.v2.backend.dto.BatchResolveDigitalObjectResponse;
import com.dris.v2.backend.dto.BatchResolveMetadataResponse;
import com.dris.v2.backend.service.IdentifierCoreService;
import com.dris.sdk.next.domain.DigitalObject;
import com.dris.sdk.next.domain.IdentifierMetadata;
import com.dris.v2.backend.contract.DrisResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Arrays;
import java.util.Collections;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = IdentifierCoreController.class)
class IdentifierCoreControllerTest {
    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @MockBean
    private IdentifierCoreService service;

    @Test
    void register_shouldReturnOk() throws Exception {
        when(service.register(any())).thenReturn(DrisResponse.success(TestSamples.VALID_IDENTIFIER));

        mockMvc.perform(post("/api/v2/identifiers/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(TestSamples.registrationRequest())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ok").value(true))
                .andExpect(jsonPath("$.data").value(TestSamples.VALID_IDENTIFIER));
    }

    @Test
    void metadata_notFound_shouldReturn404() throws Exception {
        when(service.resolveMetadata(TestSamples.VALID_IDENTIFIER_NOT_FOUND)).thenReturn(DrisResponse.failure("NOT_FOUND", "missing", null));

        mockMvc.perform(get("/api/v2/identifiers/" + TestSamples.VALID_IDENTIFIER_NOT_FOUND + "/metadata"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.ok").value(false))
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    @Test
    void metadata_shouldReturnOk() throws Exception {
        when(service.resolveMetadata(TestSamples.VALID_IDENTIFIER)).thenReturn(DrisResponse.success(TestSamples.metadata()));

        mockMvc.perform(get("/api/v2/identifiers/" + TestSamples.VALID_IDENTIFIER + "/metadata"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ok").value(true));
    }

    @Test
    void registerBatch_shouldReturnOk() throws Exception {
        when(service.registerBatch(anyList())).thenReturn(DrisResponse.success(Arrays.asList(TestSamples.VALID_IDENTIFIER, TestSamples.VALID_IDENTIFIER_2)));

        mockMvc.perform(post("/api/v2/identifiers/register/batch")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("[" + objectMapper.writeValueAsString(TestSamples.registrationRequest()) + "," + objectMapper.writeValueAsString(TestSamples.registrationRequest()) + "]"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ok").value(true))
                .andExpect(jsonPath("$.data[0]").value(TestSamples.VALID_IDENTIFIER));
    }

    @Test
    void patchMetadata_validationError_shouldReturn400() throws Exception {
        when(service.partialUpdateIdentifier(eq(TestSamples.VALID_IDENTIFIER), anyMap()))
                .thenThrow(new BusinessException("VALIDATION_ERROR", "fields is required", HttpStatus.BAD_REQUEST));

        mockMvc.perform(patch("/api/v2/identifiers/" + TestSamples.VALID_IDENTIFIER + "/metadata")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.ok").value(false))
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void updateMetadata_shouldReturnOk() throws Exception {
        when(service.updateIdentifier(eq(TestSamples.VALID_IDENTIFIER), any(IdentifierMetadata.class))).thenReturn(DrisResponse.success(Boolean.TRUE));

        mockMvc.perform(put("/api/v2/identifiers/" + TestSamples.VALID_IDENTIFIER + "/metadata")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(TestSamples.metadata())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ok").value(true))
                .andExpect(jsonPath("$.data").value(true));
    }

    @Test
    void delete_shouldReturnOk() throws Exception {
        when(service.deleteIdentifier(TestSamples.VALID_IDENTIFIER)).thenReturn(DrisResponse.success(Boolean.TRUE));

        mockMvc.perform(delete("/api/v2/identifiers/" + TestSamples.VALID_IDENTIFIER))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ok").value(true))
                .andExpect(jsonPath("$.data").value(true));
    }

    @Test
    void resolveBatch_shouldReturnOk() throws Exception {
        when(service.resolveMetadataBatch(anyList())).thenReturn(DrisResponse.success(Collections.<IdentifierMetadata>emptyList()));

        mockMvc.perform(post("/api/v2/identifiers/resolve/batch")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("[\"" + TestSamples.VALID_IDENTIFIER + "\",\"" + TestSamples.VALID_IDENTIFIER_2 + "\"]"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ok").value(true));
    }
    @Test
    void resolveBatchDistributed_shouldReturnOk() throws Exception {
        when(service.resolveMetadataBatchDistributed(anyList()))
                .thenReturn(DrisResponse.success(new BatchResolveMetadataResponse()));

        mockMvc.perform(post("/api/v2/identifiers/resolve/batch/distributed")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("[\"" + TestSamples.VALID_IDENTIFIER + "\",\"" + TestSamples.VALID_IDENTIFIER_2 + "\"]"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ok").value(true));
    }
    @Test
    void resolveObjectBatchDistributed_shouldReturnOk() throws Exception {
        when(service.resolveDigitalObjectBatchDistributed(anyList()))
                .thenReturn(DrisResponse.success(new BatchResolveDigitalObjectResponse()));

        mockMvc.perform(post("/api/v2/identifiers/resolve/batch/object/distributed")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("[\"" + TestSamples.VALID_IDENTIFIER + "\",\"" + TestSamples.VALID_IDENTIFIER_2 + "\"]"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ok").value(true));
    }
    @Test
    void validate_shouldReturnOk() throws Exception {
        when(service.validateIdentifier(TestSamples.VALID_IDENTIFIER)).thenReturn(DrisResponse.success(Boolean.TRUE));

        mockMvc.perform(get("/api/v2/identifiers/" + TestSamples.VALID_IDENTIFIER + "/validate"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ok").value(true))
                .andExpect(jsonPath("$.data").value(true));
    }

    @Test
    void exists_shouldReturnOk() throws Exception {
        when(service.existsIdentifier(TestSamples.VALID_IDENTIFIER)).thenReturn(DrisResponse.success(Boolean.TRUE));

        mockMvc.perform(get("/api/v2/identifiers/" + TestSamples.VALID_IDENTIFIER + "/exists"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ok").value(true))
                .andExpect(jsonPath("$.data").value(true));
    }

    @Test
    void object_shouldReturnOk() throws Exception {
        when(service.getDigitalObject(TestSamples.VALID_IDENTIFIER)).thenReturn(DrisResponse.success(new DigitalObject()));

        mockMvc.perform(get("/api/v2/identifiers/" + TestSamples.VALID_IDENTIFIER + "/object"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ok").value(true));
    }

    @Test
    void resolveBatch_validationError_shouldReturn400() throws Exception {
        when(service.resolveMetadataBatch(anyList()))
                .thenThrow(new BusinessException("VALIDATION_ERROR", "identifiers is required", HttpStatus.BAD_REQUEST));

        mockMvc.perform(post("/api/v2/identifiers/resolve/batch")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("[]"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.ok").value(false))
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }
}




