package com.dris.v2.backend.service;

import com.dris.v2.backend.dao.IdentifierHistoryQueryDao;
import com.dris.v2.backend.dto.IdentifierHistoryPageResponse;
import com.dris.v2.backend.model.BusinessException;
import com.dris.v2.backend.model.IdentifierHistoryRecord;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class IdentifierAuditQueryServiceTest {
    @Mock
    private IdentifierHistoryQueryDao identifierHistoryQueryDao;
    @InjectMocks
    private IdentifierAuditQueryService service;

    @Test
    void queryIdentifierHistory_valid_shouldReturnPage() {
        IdentifierHistoryRecord record = new IdentifierHistoryRecord();
        record.setId(Long.valueOf(1L));
        record.setIdentifier("1100ABCD019320260101AAAAAAAAAA");
        record.setOperation("UPDATE_METADATA");
        record.setActorUserId("u_admin");
        record.setActorRole("admin");
        record.setSuccess(true);
        record.setMessage("ok");
        record.setCreatedAt(Instant.parse("2026-03-08T02:10:00Z"));

        when(identifierHistoryQueryDao.count(eq("1100ABCD019320260101AAAAAAAAAA"), eq("UPDATE_METADATA"), any(), any())).thenReturn(1L);
        when(identifierHistoryQueryDao.findPage(eq("1100ABCD019320260101AAAAAAAAAA"), eq("UPDATE_METADATA"), any(), any(), eq(0), eq(20)))
                .thenReturn(Collections.singletonList(record));

        IdentifierHistoryPageResponse response = service.queryIdentifierHistory(
                "1100ABCD019320260101AAAAAAAAAA",
                "UPDATE_METADATA",
                "2026-03-08T00:00:00Z",
                "2026-03-08T23:59:59Z",
                1,
                20
        );

        assertEquals(1L, response.getTotal());
        assertEquals(1, response.getPage());
        assertEquals(20, response.getSize());
        assertTrue(!response.isHasNext());
        assertEquals(1, response.getRecords().size());
        assertEquals("1100ABCD019320260101AAAAAAAAAA", response.getRecords().get(0).getIdentifier());
        verify(identifierHistoryQueryDao).count(eq("1100ABCD019320260101AAAAAAAAAA"), eq("UPDATE_METADATA"), any(), any());
        verify(identifierHistoryQueryDao).findPage(eq("1100ABCD019320260101AAAAAAAAAA"), eq("UPDATE_METADATA"), any(), any(), eq(0), eq(20));
    }

    @Test
    void queryIdentifierHistory_invalidPage_shouldThrow() {
        assertThrows(BusinessException.class, () -> service.queryIdentifierHistory(null, null, null, null, 0, 20));
    }

    @Test
    void queryIdentifierHistory_invalidSize_shouldThrow() {
        assertThrows(BusinessException.class, () -> service.queryIdentifierHistory(null, null, null, null, 1, 201));
    }

    @Test
    void queryIdentifierHistory_endBeforeStart_shouldThrow() {
        assertThrows(BusinessException.class, () -> service.queryIdentifierHistory(
                null,
                null,
                "2026-03-08T12:00:00Z",
                "2026-03-08T10:00:00Z",
                1,
                20
        ));
    }

    @Test
    void queryIdentifierHistory_epochMillisTime_shouldBeAccepted() {
        when(identifierHistoryQueryDao.count(eq(null), eq(null), any(), any())).thenReturn(0L);

        IdentifierHistoryPageResponse response = service.queryIdentifierHistory(
                null,
                null,
                "1772935200000",
                "1773021599000",
                1,
                20
        );

        assertEquals(0L, response.getTotal());
        assertEquals(0, response.getRecords().size());
        verify(identifierHistoryQueryDao).count(eq(null), eq(null), any(), any());
    }
}
