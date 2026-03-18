package com.dris.v2.backend.control;

import com.dris.v2.backend.TestSamples;
import com.dris.v2.backend.dto.BatchResolveResourceResponse;
import com.dris.v2.backend.dto.IdentifierComponentVersionPageResponse;
import com.dris.v2.backend.dto.IdentifierComponentVersionView;
import com.dris.v2.backend.service.IdentifierComponentVersionQueryService;
import com.dris.v2.backend.service.IdentifierResourceService;
import com.dris.sdk.next.domain.Resource;
import com.dris.v2.backend.contract.DrisResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Collections;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = IdentifierResourceController.class)
class IdentifierResourceControllerTest {
    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @MockBean
    private IdentifierResourceService service;
    @MockBean
    private IdentifierComponentVersionQueryService versionQueryService;

    @Test
    void getComponent_shouldReturnOk() throws Exception {
        when(service.getComponent(TestSamples.VALID_IDENTIFIER)).thenReturn(DrisResponse.success(TestSamples.resource()));

        mockMvc.perform(get("/api/v2/identifiers/" + TestSamples.VALID_IDENTIFIER + "/component"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ok").value(true));
    }

    @Test
    void componentVersions_shouldReturnOk() throws Exception {
        IdentifierComponentVersionView view = new IdentifierComponentVersionView();
        view.setIdentifier(TestSamples.VALID_IDENTIFIER);
        view.setVersionNo(2L);
        view.setComponentType("resource");
        IdentifierComponentVersionPageResponse response = new IdentifierComponentVersionPageResponse();
        response.setRecords(Collections.singletonList(view));
        response.setTotal(1L);
        response.setPage(1);
        response.setSize(10);
        response.setHasNext(false);
        when(versionQueryService.queryPage(TestSamples.VALID_IDENTIFIER, Integer.valueOf(1), Integer.valueOf(10))).thenReturn(response);

        mockMvc.perform(get("/api/v2/identifiers/" + TestSamples.VALID_IDENTIFIER + "/component/versions?page=1&size=10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ok").value(true))
                .andExpect(jsonPath("$.data.records[0].versionNo").value(2));
    }

    @Test
    void componentVersion_shouldReturnOk() throws Exception {
        IdentifierComponentVersionView view = new IdentifierComponentVersionView();
        view.setIdentifier(TestSamples.VALID_IDENTIFIER);
        view.setVersionNo(3L);
        view.setComponentType("resource");
        when(versionQueryService.getOne(TestSamples.VALID_IDENTIFIER, Long.valueOf(3L))).thenReturn(view);

        mockMvc.perform(get("/api/v2/identifiers/" + TestSamples.VALID_IDENTIFIER + "/component/versions/3"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ok").value(true))
                .andExpect(jsonPath("$.data.versionNo").value(3));
    }

    @Test
    void upsertComponent_shouldReturnOk() throws Exception {
        when(service.upsertComponent(eq(TestSamples.VALID_IDENTIFIER), any(Resource.class), eq(true))).thenReturn(DrisResponse.success(Boolean.TRUE));

        mockMvc.perform(put("/api/v2/identifiers/" + TestSamples.VALID_IDENTIFIER + "/component?forceReplaceType=true")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(TestSamples.resource())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ok").value(true))
                .andExpect(jsonPath("$.data").value(true));
    }

    @Test
    void removeComponent_shouldReturnOk() throws Exception {
        when(service.removeComponent(TestSamples.VALID_IDENTIFIER)).thenReturn(DrisResponse.success(Boolean.TRUE));

        mockMvc.perform(delete("/api/v2/identifiers/" + TestSamples.VALID_IDENTIFIER + "/component"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ok").value(true));
    }

    @Test
    void resolveComponentBatchDistributed_shouldReturnOk() throws Exception {
        when(service.resolveResourceBatchDistributed(anyList()))
                .thenReturn(DrisResponse.success(new BatchResolveResourceResponse()));

        mockMvc.perform(post("/api/v2/identifiers/resolve/batch/component/distributed")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("[\"" + TestSamples.VALID_IDENTIFIER + "\",\"" + TestSamples.VALID_IDENTIFIER_2 + "\"]"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ok").value(true));
    }

    @Test
    void getComponent_notFound_shouldReturn404() throws Exception {
        when(service.getComponent(TestSamples.VALID_IDENTIFIER_NOT_FOUND)).thenReturn(DrisResponse.failure("NOT_FOUND", "missing", null));

        mockMvc.perform(get("/api/v2/identifiers/" + TestSamples.VALID_IDENTIFIER_NOT_FOUND + "/component"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.ok").value(false))
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }
}
