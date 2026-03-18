package com.dris.v2.backend.control;

import com.dris.v2.backend.dto.GlobalSearchResponse;
import com.dris.v2.backend.model.BusinessException;
import com.dris.v2.backend.service.DrirpSearchService;
import com.dris.v2.backend.service.FederatedSearchService;
import com.dris.v2.backend.contract.search.SearchResult;
import com.dris.v2.backend.contract.DrisResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Collections;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = SearchController.class)
class SearchControllerTest {
    @Autowired
    private MockMvc mockMvc;
    @MockBean
    private FederatedSearchService service;
    @MockBean
    private DrirpSearchService drirpSearchService;

    @Test
    void search_shouldReturnOk() throws Exception {
        when(service.search(any())).thenReturn(DrisResponse.success(Collections.<SearchResult>emptyList()));

        mockMvc.perform(post("/api/v2/search")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"naturalQuery\":\"traffic monitoring camera\",\"keywords\":[\"camera\",\"intersection\"],\"limit\":20,\"offset\":0,\"mode\":\"HYBRID\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ok").value(true));
    }

    @Test
    void search_validationError_shouldReturn400() throws Exception {
        when(service.search(any()))
                .thenThrow(new BusinessException("VALIDATION_ERROR", "request is required", HttpStatus.BAD_REQUEST));

        mockMvc.perform(post("/api/v2/search")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.ok").value(false))
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void globalSearch_shouldReturnOk() throws Exception {
        when(drirpSearchService.searchDistributed(any())).thenReturn(DrisResponse.success(new GlobalSearchResponse()));

        mockMvc.perform(post("/api/v2/search/global")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"naturalQuery\":\"wind power prediction\",\"strategy\":\"ITERATIVE\",\"anchorNodeCode\":\"3700ABCD\",\"limit\":10,\"offset\":0}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ok").value(true));
    }

    @Test
    void globalSearch_validationError_shouldReturn400() throws Exception {
        when(drirpSearchService.searchDistributed(any()))
                .thenThrow(new BusinessException("VALIDATION_ERROR", "request is required", HttpStatus.BAD_REQUEST));

        mockMvc.perform(post("/api/v2/search/global")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.ok").value(false))
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }
}
