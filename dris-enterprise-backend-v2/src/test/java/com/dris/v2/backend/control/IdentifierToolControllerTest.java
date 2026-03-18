package com.dris.v2.backend.control;

import com.dris.v2.backend.TestSamples;
import com.dris.v2.backend.dto.ToolExecuteResult;
import com.dris.v2.backend.service.IdentifierToolExecutionService;
import com.dris.v2.backend.contract.DrisResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = IdentifierToolController.class)
class IdentifierToolControllerTest {
    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @MockBean
    private IdentifierToolExecutionService service;

    @Test
    void executeTool_shouldReturnOk() throws Exception {
        ToolExecuteResult result = new ToolExecuteResult();
        result.setSuccess(true);
        result.setExitCode(0);
        result.setStdout("12");
        when(service.executeTool(eq(TestSamples.VALID_IDENTIFIER), any())).thenReturn(DrisResponse.success(result));

        Map<String, Object> req = new LinkedHashMap<String, Object>();
        Map<String, Object> args = new LinkedHashMap<String, Object>();
        args.put("a", 7);
        args.put("b", 5);
        req.put("args", args);
        req.put("timeoutMs", 5000);

        mockMvc.perform(post("/api/v2/identifiers/" + TestSamples.VALID_IDENTIFIER + "/tool/execute")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ok").value(true))
                .andExpect(jsonPath("$.data.success").value(true))
                .andExpect(jsonPath("$.data.exitCode").value(0));
    }

    @Test
    void executeTool_notFound_shouldReturn404() throws Exception {
        when(service.executeTool(eq(TestSamples.VALID_IDENTIFIER_NOT_FOUND), any()))
                .thenReturn(DrisResponse.failure("NOT_FOUND", "tool resource not found", null));

        mockMvc.perform(post("/api/v2/identifiers/" + TestSamples.VALID_IDENTIFIER_NOT_FOUND + "/tool/execute")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.ok").value(false))
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }
}
