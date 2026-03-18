package com.dris.v2.backend.service;

import com.dris.v2.backend.dto.ToolExecuteResult;
import com.dris.v2.backend.config.BackendV2Properties;
import com.dris.v2.backend.config.DrirpSecuritySupport;
import com.dris.v2.backend.dao.DirectDescendantDao;
import com.dris.v2.backend.dao.RouteForwardDao;
import com.dris.v2.backend.model.NodeDrirpOpcode;
import com.dris.v2.backend.risk.RiskDecisionResult;
import com.dris.v2.backend.risk.RiskDecisionService;
import com.dris.v2.backend.service.local.upload.LocalUploadService;
import com.dris.v2.backend.contract.upload.DownloadPresignRequest;
import com.dris.v2.backend.contract.upload.DownloadPresignResult;
import com.dris.v2.backend.contract.DrisResponse;
import com.google.gson.JsonObject;
import org.bdware.drirp.model.DrirpMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DrirpOperationServiceTest {
    @Mock
    private LocalUploadService localUploadService;
    @Mock
    private ToolExecutionLocalService toolExecutionLocalService;
    @Mock
    private RouteForwardDao routeForwardDao;
    @Mock
    private DirectDescendantDao directDescendantDao;
    @Mock
    private RiskDecisionService riskDecisionService;
    @Mock
    private DrirpSecuritySupport drirpSecuritySupport;
    @Mock
    private DrirpAuditService drirpAuditService;

    @BeforeEach
    void setUp() {
        lenient().when(riskDecisionService.evaluateToolExecution(anyString(), anyString(), any())).thenReturn(RiskDecisionResult.allow("permit-all"));
        lenient().when(riskDecisionService.evaluateToolExecution(isNull(), anyString(), any())).thenReturn(RiskDecisionResult.allow("permit-all"));
        lenient().when(riskDecisionService.evaluateDownload(anyString(), anyString(), any())).thenReturn(RiskDecisionResult.allow("permit-all"));
        lenient().when(riskDecisionService.evaluateDownload(isNull(), anyString(), any())).thenReturn(RiskDecisionResult.allow("permit-all"));
    }

    @Test
    void executeToolDistributed_localTargetShouldExecuteLocally() {
        BackendV2Properties properties = new BackendV2Properties();
        properties.setRegionCode("3701");
        properties.setOrganizationCode("WXYZ");
        properties.setDrirpEnabled(true);

        ToolExecuteResult result = new ToolExecuteResult();
        result.setIdentifier("3701WXYZ019320260101AAAAAAAAAA");
        result.setSuccess(true);
        when(toolExecutionLocalService.executeToolLocal("3701WXYZ019320260101AAAAAAAAAA", null))
                .thenReturn(DrisResponse.success(result));

        DrirpOperationService service = new DrirpOperationService(
                localUploadService,
                toolExecutionLocalService,
                properties,
                routeForwardDao,
                directDescendantDao,
                riskDecisionService,
                drirpSecuritySupport,
                drirpAuditService
        );

        DrisResponse<ToolExecuteResult> response = service.executeToolDistributed("3701WXYZ019320260101AAAAAAAAAA", null);

        assertTrue(response.isOk());
        assertEquals("3701WXYZ", response.getData().getHandledByNodeCode());
        verify(riskDecisionService).evaluateToolExecution(isNull(), anyString(), any());
        verify(toolExecutionLocalService).executeToolLocal("3701WXYZ019320260101AAAAAAAAAA", null);
    }

    @Test
    void executeToolDistributed_riskDeniedShouldReturnForbidden() {
        BackendV2Properties properties = new BackendV2Properties();
        properties.setRegionCode("3701");
        properties.setOrganizationCode("WXYZ");
        properties.setDrirpEnabled(true);
        when(riskDecisionService.evaluateToolExecution(isNull(), anyString(), any()))
                .thenReturn(RiskDecisionResult.deny("tool denied"));

        DrirpOperationService service = new DrirpOperationService(
                localUploadService,
                toolExecutionLocalService,
                properties,
                routeForwardDao,
                directDescendantDao,
                riskDecisionService,
                drirpSecuritySupport,
                drirpAuditService
        );

        DrisResponse<ToolExecuteResult> response = service.executeToolDistributed("3701WXYZ019320260101AAAAAAAAAA", null);

        assertTrue(!response.isOk());
        assertEquals("FORBIDDEN", response.getCode());
        assertEquals("tool denied", response.getMessage());
    }

    @Test
    void handleOperationForward_parentFallbackShouldCarryParentNodeCode() {
        BackendV2Properties properties = new BackendV2Properties();
        properties.setRegionCode("1100");
        properties.setOrganizationCode("AAAA");
        properties.setResolveMode("iterative");
        properties.setResolveAllowParentFallback(true);
        properties.setParentDrirpUrl("grpc://127.0.0.1:29090");
        properties.setParentRegionCode("3700");
        properties.setParentOrganizationCode("ABCD");
        properties.setDrirpPublicUrl("grpc://127.0.0.1:19090");

        when(routeForwardDao.findByTargetCode("3701WXYZ")).thenReturn(null);
        when(directDescendantDao.findByChildCode("3701WXYZ")).thenReturn(null);

        DrirpOperationService service = new DrirpOperationService(
                localUploadService,
                toolExecutionLocalService,
                properties,
                routeForwardDao,
                directDescendantDao,
                riskDecisionService,
                drirpSecuritySupport,
                drirpAuditService
        );

        JsonObject body = new JsonObject();
        body.addProperty("identifier", "3701WXYZ019320260101AAAAAAAAAA");
        body.addProperty("operationType", "TOOL_EXECUTE");
        body.addProperty("ttl", 8);
        DrirpMessage request = new DrirpMessage(NodeDrirpOpcode.OP_FORWARD, body);

        DrirpMessage response = service.handleOperationForward(request);

        assertTrue(response.isSuccess());
        JsonObject out = response.getBody().getAsJsonObject();
        assertTrue(out.get("delegate").getAsBoolean());
        assertEquals("3700ABCD", out.get("nextHopCode").getAsString());
        assertEquals("grpc://127.0.0.1:29090", out.get("nextHopDrirpUrl").getAsString());
    }

    @Test
    void handleOperationForward_toolExecuteShouldReturnToolResult() {
        BackendV2Properties properties = new BackendV2Properties();
        properties.setRegionCode("3701");
        properties.setOrganizationCode("WXYZ");
        properties.setResolveMode("recursive");
        properties.setDrirpEnabled(true);
        properties.setDrirpPublicUrl("grpc://127.0.0.1:19090");

        ToolExecuteResult result = new ToolExecuteResult();
        result.setIdentifier("3701WXYZ019320260101AAAAAAAAAA");
        result.setSuccess(true);
        result.setStdout("12");
        when(toolExecutionLocalService.executeToolLocal("3701WXYZ019320260101AAAAAAAAAA", null))
                .thenReturn(DrisResponse.success(result));

        DrirpOperationService service = new DrirpOperationService(
                localUploadService,
                toolExecutionLocalService,
                properties,
                routeForwardDao,
                directDescendantDao,
                riskDecisionService,
                drirpSecuritySupport,
                drirpAuditService
        );

        JsonObject body = new JsonObject();
        body.addProperty("identifier", "3701WXYZ019320260101AAAAAAAAAA");
        body.addProperty("operationType", "TOOL_EXECUTE");
        body.addProperty("ttl", 8);
        body.addProperty("userId", "u_test");
        DrirpMessage request = new DrirpMessage(NodeDrirpOpcode.OP_FORWARD, body);

        DrirpMessage response = service.handleOperationForward(request);

        assertTrue(response.isSuccess());
        JsonObject out = response.getBody().getAsJsonObject();
        assertEquals("TOOL_EXECUTE", out.get("operationType").getAsString());
        assertEquals("3701WXYZ", out.get("handledBy").getAsString());
        assertEquals("12", out.getAsJsonObject("toolExecuteResult").get("stdout").getAsString());
        verify(riskDecisionService).evaluateToolExecution(anyString(), anyString(), any());
    }

    @Test
    void createDownloadPresignDistributed_localTargetShouldUsePresignResolver() {
        BackendV2Properties properties = new BackendV2Properties();
        properties.setRegionCode("3701");
        properties.setOrganizationCode("WXYZ");
        properties.setDrirpEnabled(true);

        DownloadPresignRequest request = new DownloadPresignRequest();
        request.setIdentifier("3701WXYZ019320260101AAAAAAAAAA");
        request.setExpiresInSeconds(600);

        DownloadPresignResult result = new DownloadPresignResult();
        result.setUrl("http://127.0.0.1:8080/download/object");
        when(localUploadService.createDownloadPresign(any(DownloadPresignRequest.class))).thenReturn(DrisResponse.success(result));

        DrirpOperationService service = new DrirpOperationService(
                localUploadService,
                toolExecutionLocalService,
                properties,
                routeForwardDao,
                directDescendantDao,
                riskDecisionService,
                drirpSecuritySupport,
                drirpAuditService
        );

        DrisResponse<DownloadPresignResult> response = service.createDownloadPresignDistributed(request);

        assertTrue(response.isOk());
        assertEquals("http://127.0.0.1:8080/download/object", response.getData().getUrl());
        verify(riskDecisionService).evaluateDownload(isNull(), anyString(), any());
        verify(localUploadService).createDownloadPresign(any(DownloadPresignRequest.class));
    }

    @Test
    void handleOperationForward_downloadPresignShouldReturnDownloadResultField() {
        BackendV2Properties properties = new BackendV2Properties();
        properties.setRegionCode("3701");
        properties.setOrganizationCode("WXYZ");
        properties.setResolveMode("recursive");
        properties.setDrirpEnabled(true);
        properties.setDrirpPublicUrl("grpc://127.0.0.1:19090");

        DownloadPresignRequest request = new DownloadPresignRequest();
        request.setIdentifier("3701WXYZ019320260101AAAAAAAAAA");
        request.setExpiresInSeconds(600);
        DownloadPresignResult result = new DownloadPresignResult();
        result.setUrl("http://127.0.0.1:8080/download/object");
        when(localUploadService.createDownloadPresign(any(DownloadPresignRequest.class))).thenReturn(DrisResponse.success(result));

        DrirpOperationService service = new DrirpOperationService(
                localUploadService,
                toolExecutionLocalService,
                properties,
                routeForwardDao,
                directDescendantDao,
                riskDecisionService,
                drirpSecuritySupport,
                drirpAuditService
        );

        JsonObject body = new JsonObject();
        body.addProperty("identifier", "3701WXYZ019320260101AAAAAAAAAA");
        body.addProperty("operationType", "DOWNLOAD_PRESIGN");
        body.addProperty("ttl", 8);
        body.addProperty("userId", "u_test");
        JsonObject presignRequest = new JsonObject();
        presignRequest.addProperty("identifier", "3701WXYZ019320260101AAAAAAAAAA");
        presignRequest.addProperty("expiresInSeconds", 600);
        body.add("downloadPresignRequest", presignRequest);
        DrirpMessage requestMessage = new DrirpMessage(NodeDrirpOpcode.OP_FORWARD, body);

        DrirpMessage response = service.handleOperationForward(requestMessage);

        assertTrue(response.isSuccess());
        JsonObject out = response.getBody().getAsJsonObject();
        assertEquals("DOWNLOAD_PRESIGN", out.get("operationType").getAsString());
        assertEquals("3701WXYZ", out.get("handledBy").getAsString());
        assertEquals("http://127.0.0.1:8080/download/object", out.getAsJsonObject("downloadPresignResult").get("url").getAsString());
        verify(riskDecisionService).evaluateDownload(anyString(), anyString(), any());
    }
}
