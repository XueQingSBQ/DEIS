package com.dris.v2.backend.control;

import com.dris.v2.backend.TestSamples;
import com.dris.v2.backend.service.IdentifierUploadService;
import com.dris.sdk.next.domain.Resource;
import com.dris.v2.backend.contract.upload.DownloadPresignResult;
import com.dris.v2.backend.contract.DrisResponse;
import com.dris.v2.backend.contract.upload.UploadCreateSessionResult;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = IdentifierUploadController.class)
class IdentifierUploadControllerTest {
    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @MockBean
    private IdentifierUploadService service;

    @Test
    void createUploadSession_shouldReturnOk() throws Exception {
        when(service.createUploadSession(eq(TestSamples.VALID_IDENTIFIER), any())).thenReturn(DrisResponse.success(new UploadCreateSessionResult()));

        mockMvc.perform(post("/api/v2/identifiers/" + TestSamples.VALID_IDENTIFIER + "/upload-sessions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(TestSamples.uploadSessionRequest())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ok").value(true));
    }

    @Test
    void completeUpload_shouldReturnOk() throws Exception {
        when(service.completeUpload("up-1")).thenReturn(DrisResponse.success(new Resource()));

        mockMvc.perform(post("/api/v2/upload-sessions/up-1/complete"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ok").value(true));
    }

    @Test
    void createDownloadPresign_shouldReturnOk() throws Exception {
        when(service.createDownloadPresign(any())).thenReturn(DrisResponse.success(new DownloadPresignResult()));

        mockMvc.perform(post("/api/v2/downloads/presign")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(TestSamples.downloadPresignRequest())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ok").value(true));
    }

    @Test
    void completeUpload_notFound_shouldReturn404() throws Exception {
        when(service.completeUpload("up-404")).thenReturn(DrisResponse.failure("NOT_FOUND", "missing", null));

        mockMvc.perform(post("/api/v2/upload-sessions/up-404/complete"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.ok").value(false))
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }
}
