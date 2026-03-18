package com.dris.v2.backend.control;

import com.dris.v2.backend.service.SystemOpsService;
import com.dris.v2.backend.contract.DrisResponse;
import com.dris.v2.backend.contract.system.VectorSyncResult;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Collections;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = SystemOpsController.class)
class SystemOpsControllerTest {
    @Autowired
    private MockMvc mockMvc;
    @MockBean
    private SystemOpsService service;

    @Test
    void syncVector_shouldReturnOk() throws Exception {
        when(service.syncVectorIndex()).thenReturn(DrisResponse.success(new VectorSyncResult()));

        mockMvc.perform(post("/api/v2/ops/vector/sync"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ok").value(true));
    }

    @Test
    void metrics_shouldReturnOk() throws Exception {
        when(service.metricsSnapshot()).thenReturn(Collections.singletonMap("search", Collections.singletonMap("ok", 1L)));

        mockMvc.perform(get("/api/v2/ops/metrics"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ok").value(true))
                .andExpect(jsonPath("$.data.search.ok").value(1));
    }

    @Test
    void syncVector_failure_shouldReturn400() throws Exception {
        when(service.syncVectorIndex()).thenReturn(DrisResponse.failure("VALIDATION_ERROR", "bad", null));

        mockMvc.perform(post("/api/v2/ops/vector/sync"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.ok").value(false))
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }
}
