package com.dris.v2.backend.service;

import com.dris.v2.backend.service.local.system.LocalSystemOpsService;
import com.dris.v2.backend.contract.DrisResponse;
import com.dris.v2.backend.contract.system.VectorSyncResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SystemOpsServiceTest {
    @Mock
    private LocalSystemOpsService localSystemOpsService;
    @InjectMocks
    private SystemOpsService service;

    @Test
    void syncVector_shouldDelegate() {
        when(localSystemOpsService.syncVectorIndex()).thenReturn(DrisResponse.success(new VectorSyncResult()));
        DrisResponse<VectorSyncResult> out = service.syncVectorIndex();
        assertTrue(out.isOk());
        verify(localSystemOpsService).syncVectorIndex();
    }

    @Test
    void metrics_shouldDelegate() {
        Map<String, Map<String, Long>> metrics = Collections.singletonMap("search", Collections.singletonMap("ok", 1L));
        when(localSystemOpsService.metricsSnapshot()).thenReturn(metrics);
        Map<String, Map<String, Long>> out = service.metricsSnapshot();
        assertSame(metrics, out);
        verify(localSystemOpsService).metricsSnapshot();
    }
}
