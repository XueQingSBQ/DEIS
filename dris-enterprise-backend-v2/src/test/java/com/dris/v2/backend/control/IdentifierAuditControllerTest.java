package com.dris.v2.backend.control;

import com.dris.v2.backend.dto.IdentifierHistoryPageResponse;
import com.dris.v2.backend.dto.IdentifierHistoryRecordView;
import com.dris.v2.backend.model.BusinessException;
import com.dris.v2.backend.service.IdentifierAuditQueryService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Collections;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = IdentifierAuditController.class)
class IdentifierAuditControllerTest {
    @Autowired
    private MockMvc mockMvc;
    @MockBean
    private IdentifierAuditQueryService identifierAuditQueryService;

    @Test
    void identifierHistory_shouldReturnOk() throws Exception {
        IdentifierHistoryRecordView view = new IdentifierHistoryRecordView();
        view.setId(Long.valueOf(1L));
        view.setIdentifier("1100ABCD019320260101AAAAAAAAAA");
        view.setOperation("UPDATE_METADATA");
        view.setSuccess(true);
        view.setCreatedAt("2026-03-08T02:10:00Z");

        IdentifierHistoryPageResponse response = new IdentifierHistoryPageResponse();
        response.setRecords(Collections.singletonList(view));
        response.setTotal(1L);
        response.setPage(1);
        response.setSize(20);
        response.setHasNext(false);

        when(identifierAuditQueryService.queryIdentifierHistory(
                eq("1100ABCD019320260101AAAAAAAAAA"),
                eq("UPDATE_METADATA"),
                eq("2026-03-08T00:00:00Z"),
                eq("2026-03-08T23:59:59Z"),
                eq(Integer.valueOf(1)),
                eq(Integer.valueOf(20))
        )).thenReturn(response);

        mockMvc.perform(get("/api/v2/audit/identifier-history")
                        .param("identifier", "1100ABCD019320260101AAAAAAAAAA")
                        .param("operation", "UPDATE_METADATA")
                        .param("startTime", "2026-03-08T00:00:00Z")
                        .param("endTime", "2026-03-08T23:59:59Z")
                        .param("page", "1")
                        .param("size", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ok").value(true))
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.page").value(1))
                .andExpect(jsonPath("$.data.records[0].identifier").value("1100ABCD019320260101AAAAAAAAAA"));
    }

    @Test
    void identifierHistory_validationError_shouldReturn400() throws Exception {
        when(identifierAuditQueryService.queryIdentifierHistory(
                eq(null),
                eq(null),
                eq(null),
                eq(null),
                eq(Integer.valueOf(0)),
                eq(Integer.valueOf(20))
        )).thenThrow(new BusinessException("VALIDATION_ERROR", "page must be greater than or equal to 1", HttpStatus.BAD_REQUEST));

        mockMvc.perform(get("/api/v2/audit/identifier-history")
                        .param("page", "0")
                        .param("size", "20"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.ok").value(false))
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void identifierHistory_invalidPageType_shouldReturn400() throws Exception {
        mockMvc.perform(get("/api/v2/audit/identifier-history")
                        .param("page", "abc")
                        .param("size", "20"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.ok").value(false))
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }
}
