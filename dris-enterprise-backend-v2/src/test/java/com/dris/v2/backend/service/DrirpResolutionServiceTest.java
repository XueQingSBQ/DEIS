package com.dris.v2.backend.service;

import com.dris.v2.backend.sdknext.LocalDrisKernelFacade;
import com.dris.sdk.next.domain.DigitalObject;
import com.dris.sdk.next.domain.IdentifierMetadata;
import com.dris.sdk.next.domain.Resource;
import com.dris.v2.backend.contract.DrisResponse;
import com.dris.v2.backend.config.BackendV2Properties;
import com.dris.v2.backend.config.DrirpSecuritySupport;
import com.dris.v2.backend.dao.DirectDescendantDao;
import com.dris.v2.backend.dao.RouteForwardDao;
import com.dris.v2.backend.dto.BatchResolveDigitalObjectResponse;
import com.dris.v2.backend.dto.BatchResolveMetadataResponse;
import com.dris.v2.backend.dto.BatchResolveResourceResponse;
import com.dris.v2.backend.model.NodeDrirpOpcode;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.bdware.drirp.model.DrirpMessage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DrirpResolutionServiceTest {
    @Mock
    private LocalDrisKernelFacade localDrisKernelFacade;
    @Mock
    private RouteForwardDao routeForwardDao;
    @Mock
    private DirectDescendantDao directDescendantDao;
    @Mock
    private DrirpSecuritySupport drirpSecuritySupport;
    @Mock
    private DrirpAuditService drirpAuditService;

    @Test
    void handleResolveForward_parentFallbackShouldCarryParentNodeCode() {
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

        DrirpResolutionService service = new DrirpResolutionService(
                localDrisKernelFacade,
                properties,
                routeForwardDao,
                directDescendantDao,
                drirpSecuritySupport,
                drirpAuditService
        );

        JsonObject body = new JsonObject();
        body.addProperty("identifier", "3701WXYZ019320260101AAAAAAAAAA");
        body.addProperty("ttl", 8);
        DrirpMessage request = new DrirpMessage(NodeDrirpOpcode.RESOLVE_FORWARD, body);

        DrirpMessage response = service.handleResolveForward(request);

        assertTrue(response.isSuccess());
        JsonObject out = response.getBody().getAsJsonObject();
        assertTrue(out.get("delegate").getAsBoolean());
        assertEquals("3700ABCD", out.get("nextHopCode").getAsString());
        assertEquals("grpc://127.0.0.1:29090", out.get("nextHopDrirpUrl").getAsString());
    }

    @Test
    void handleResolveBatchForward_parentFallbackShouldCarryParentNodeCode() {
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

        DrirpResolutionService service = new DrirpResolutionService(
                localDrisKernelFacade,
                properties,
                routeForwardDao,
                directDescendantDao,
                drirpSecuritySupport,
                drirpAuditService
        );

        JsonArray identifiers = new JsonArray();
        identifiers.add("3701WXYZ019320260101AAAAAAAAAA");
        identifiers.add("3701WXYZ019320260101BBBBBBBBBB");
        JsonObject body = new JsonObject();
        body.add("identifiers", identifiers);
        body.addProperty("ttl", 8);
        DrirpMessage request = new DrirpMessage(NodeDrirpOpcode.RESOLVE_BATCH_FORWARD, body);

        DrirpMessage response = service.handleResolveBatchForward(request);

        assertTrue(response.isSuccess());
        JsonObject out = response.getBody().getAsJsonObject();
        assertTrue(out.get("delegate").getAsBoolean());
        assertEquals("3700ABCD", out.get("nextHopCode").getAsString());
        assertEquals("grpc://127.0.0.1:29090", out.get("nextHopDrirpUrl").getAsString());
    }

    @Test
    void resolveMetadataBatchDistributed_localTargetShouldReturnOrderedItems() {
        BackendV2Properties properties = new BackendV2Properties();
        properties.setRegionCode("1100");
        properties.setOrganizationCode("ABCD");
        properties.setDrirpEnabled(true);

        IdentifierMetadata metadata1 = new IdentifierMetadata();
        metadata1.setIdentifier("1100ABCD0211202603051234567895");
        IdentifierMetadata metadata2 = new IdentifierMetadata();
        metadata2.setIdentifier("1100ABCD021120260305ABCDE1234E");
        when(localDrisKernelFacade.getMetadata("1100ABCD0211202603051234567895"))
                .thenReturn(metadata1);
        when(localDrisKernelFacade.getMetadata("1100ABCD021120260305ABCDE1234E"))
                .thenReturn(metadata2);

        DrirpResolutionService service = new DrirpResolutionService(
                localDrisKernelFacade,
                properties,
                routeForwardDao,
                directDescendantDao,
                drirpSecuritySupport,
                drirpAuditService
        );

        DrisResponse<BatchResolveMetadataResponse> response = service.resolveMetadataBatchDistributed(
                Arrays.asList("1100ABCD0211202603051234567895", "1100ABCD021120260305ABCDE1234E")
        );

        assertTrue(response.isOk());
        assertEquals(2, response.getData().getSuccessCount());
        assertEquals(0, response.getData().getFailureCount());
        assertEquals("1100ABCD0211202603051234567895", response.getData().getItems().get(0).getMetadata().getIdentifier());
        assertEquals("1100ABCD021120260305ABCDE1234E", response.getData().getItems().get(1).getMetadata().getIdentifier());
        verify(localDrisKernelFacade).getMetadata("1100ABCD0211202603051234567895");
        verify(localDrisKernelFacade).getMetadata("1100ABCD021120260305ABCDE1234E");
    }

    @Test
    void resolveDigitalObjectDistributed_localTargetShouldUseDigitalObjectResolver() {
        BackendV2Properties properties = new BackendV2Properties();
        properties.setRegionCode("3701");
        properties.setOrganizationCode("WXYZ");
        properties.setDrirpEnabled(true);

        DigitalObject object = new DigitalObject();
        object.setIdentifier("3701WXYZ019320260101AAAAAAAAAA");
        when(localDrisKernelFacade.getDigitalObject("3701WXYZ019320260101AAAAAAAAAA"))
                .thenReturn(object);

        DrirpResolutionService service = new DrirpResolutionService(
                localDrisKernelFacade,
                properties,
                routeForwardDao,
                directDescendantDao,
                drirpSecuritySupport,
                drirpAuditService
        );

        DrisResponse<DigitalObject> response = service.resolveDigitalObjectDistributed("3701WXYZ019320260101AAAAAAAAAA");

        assertTrue(response.isOk());
        assertEquals("3701WXYZ019320260101AAAAAAAAAA", response.getData().getIdentifier());
        verify(localDrisKernelFacade).getDigitalObject("3701WXYZ019320260101AAAAAAAAAA");
    }

    @Test
    void handleResolveForward_digitalObjectShouldReturnDigitalObjectField() {
        BackendV2Properties properties = new BackendV2Properties();
        properties.setRegionCode("3701");
        properties.setOrganizationCode("WXYZ");
        properties.setResolveMode("recursive");
        properties.setDrirpEnabled(true);
        properties.setDrirpPublicUrl("grpc://127.0.0.1:19090");

        DigitalObject object = new DigitalObject();
        object.setIdentifier("3701WXYZ019320260101AAAAAAAAAA");
        when(localDrisKernelFacade.getDigitalObject("3701WXYZ019320260101AAAAAAAAAA"))
                .thenReturn(object);

        DrirpResolutionService service = new DrirpResolutionService(
                localDrisKernelFacade,
                properties,
                routeForwardDao,
                directDescendantDao,
                drirpSecuritySupport,
                drirpAuditService
        );

        JsonObject body = new JsonObject();
        body.addProperty("identifier", "3701WXYZ019320260101AAAAAAAAAA");
        body.addProperty("resolveType", "DIGITAL_OBJECT");
        body.addProperty("ttl", 8);
        DrirpMessage request = new DrirpMessage(NodeDrirpOpcode.RESOLVE_FORWARD, body);

        DrirpMessage response = service.handleResolveForward(request);

        assertTrue(response.isSuccess());
        JsonObject out = response.getBody().getAsJsonObject();
        assertEquals("DIGITAL_OBJECT", out.get("resolveType").getAsString());
        assertEquals("3701WXYZ019320260101AAAAAAAAAA", out.getAsJsonObject("digitalObject").get("identifier").getAsString());
    }

    @Test
    void resolveResourceDistributed_localTargetShouldUseComponentResolver() {
        BackendV2Properties properties = new BackendV2Properties();
        properties.setRegionCode("3701");
        properties.setOrganizationCode("WXYZ");
        properties.setDrirpEnabled(true);

        Resource resource = new Resource();
        resource.setComponentType("dataset");
        resource.setLocator("cos://bucket/object");
        when(localDrisKernelFacade.getResource("3701WXYZ019320260101AAAAAAAAAA"))
                .thenReturn(resource);

        DrirpResolutionService service = new DrirpResolutionService(
                localDrisKernelFacade,
                properties,
                routeForwardDao,
                directDescendantDao,
                drirpSecuritySupport,
                drirpAuditService
        );

        DrisResponse<Resource> response = service.resolveResourceDistributed("3701WXYZ019320260101AAAAAAAAAA");

        assertTrue(response.isOk());
        assertEquals("dataset", response.getData().getComponentType());
        assertEquals("cos://bucket/object", response.getData().getLocator());
        verify(localDrisKernelFacade).getResource("3701WXYZ019320260101AAAAAAAAAA");
    }

    @Test
    void handleResolveForward_resourceShouldReturnResourceField() {
        BackendV2Properties properties = new BackendV2Properties();
        properties.setRegionCode("3701");
        properties.setOrganizationCode("WXYZ");
        properties.setResolveMode("recursive");
        properties.setDrirpEnabled(true);
        properties.setDrirpPublicUrl("grpc://127.0.0.1:19090");

        Resource resource = new Resource();
        resource.setComponentType("dataset");
        resource.setLocator("cos://bucket/object");
        when(localDrisKernelFacade.getResource("3701WXYZ019320260101AAAAAAAAAA"))
                .thenReturn(resource);

        DrirpResolutionService service = new DrirpResolutionService(
                localDrisKernelFacade,
                properties,
                routeForwardDao,
                directDescendantDao,
                drirpSecuritySupport,
                drirpAuditService
        );

        JsonObject body = new JsonObject();
        body.addProperty("identifier", "3701WXYZ019320260101AAAAAAAAAA");
        body.addProperty("resolveType", "RESOURCE");
        body.addProperty("ttl", 8);
        DrirpMessage request = new DrirpMessage(NodeDrirpOpcode.RESOLVE_FORWARD, body);

        DrirpMessage response = service.handleResolveForward(request);

        assertTrue(response.isSuccess());
        JsonObject out = response.getBody().getAsJsonObject();
        assertEquals("RESOURCE", out.get("resolveType").getAsString());
        assertEquals("dataset", out.getAsJsonObject("resource").get("componentType").getAsString());
        assertEquals("cos://bucket/object", out.getAsJsonObject("resource").get("locator").getAsString());
    }
    @Test
    void resolveDigitalObjectBatchDistributed_localTargetShouldUseObjectResolver() {
        BackendV2Properties properties = new BackendV2Properties();
        properties.setRegionCode("3701");
        properties.setOrganizationCode("WXYZ");
        properties.setDrirpEnabled(true);

        DigitalObject first = new DigitalObject();
        first.setIdentifier("3701WXYZ019320260101AAAAAAAAAA");
        DigitalObject second = new DigitalObject();
        second.setIdentifier("3701WXYZ019320260101BBBBBBBBBB");
        when(localDrisKernelFacade.getDigitalObject("3701WXYZ019320260101AAAAAAAAAA"))
                .thenReturn(first);
        when(localDrisKernelFacade.getDigitalObject("3701WXYZ019320260101BBBBBBBBBB"))
                .thenReturn(second);

        DrirpResolutionService service = new DrirpResolutionService(
                localDrisKernelFacade,
                properties,
                routeForwardDao,
                directDescendantDao,
                drirpSecuritySupport,
                drirpAuditService
        );

        DrisResponse<BatchResolveDigitalObjectResponse> response = service.resolveDigitalObjectBatchDistributed(
                Arrays.asList("3701WXYZ019320260101AAAAAAAAAA", "3701WXYZ019320260101BBBBBBBBBB")
        );

        assertTrue(response.isOk());
        assertEquals(2, response.getData().getSuccessCount());
        assertEquals("3701WXYZ019320260101AAAAAAAAAA", response.getData().getItems().get(0).getDigitalObject().getIdentifier());
        assertEquals("3701WXYZ019320260101BBBBBBBBBB", response.getData().getItems().get(1).getDigitalObject().getIdentifier());
        verify(localDrisKernelFacade).getDigitalObject("3701WXYZ019320260101AAAAAAAAAA");
        verify(localDrisKernelFacade).getDigitalObject("3701WXYZ019320260101BBBBBBBBBB");
    }

    @Test
    void resolveResourceBatchDistributed_localTargetShouldUseComponentResolver() {
        BackendV2Properties properties = new BackendV2Properties();
        properties.setRegionCode("3701");
        properties.setOrganizationCode("WXYZ");
        properties.setDrirpEnabled(true);

        Resource first = new Resource();
        first.setComponentType("dataset");
        first.setLocator("cos://bucket/object-1");
        Resource second = new Resource();
        second.setComponentType("dataset");
        second.setLocator("cos://bucket/object-2");
        when(localDrisKernelFacade.getResource("3701WXYZ019320260101AAAAAAAAAA"))
                .thenReturn(first);
        when(localDrisKernelFacade.getResource("3701WXYZ019320260101BBBBBBBBBB"))
                .thenReturn(second);

        DrirpResolutionService service = new DrirpResolutionService(
                localDrisKernelFacade,
                properties,
                routeForwardDao,
                directDescendantDao,
                drirpSecuritySupport,
                drirpAuditService
        );

        DrisResponse<BatchResolveResourceResponse> response = service.resolveResourceBatchDistributed(
                Arrays.asList("3701WXYZ019320260101AAAAAAAAAA", "3701WXYZ019320260101BBBBBBBBBB")
        );

        assertTrue(response.isOk());
        assertEquals(2, response.getData().getSuccessCount());
        assertEquals("cos://bucket/object-1", response.getData().getItems().get(0).getResource().getLocator());
        assertEquals("cos://bucket/object-2", response.getData().getItems().get(1).getResource().getLocator());
        verify(localDrisKernelFacade).getResource("3701WXYZ019320260101AAAAAAAAAA");
        verify(localDrisKernelFacade).getResource("3701WXYZ019320260101BBBBBBBBBB");
    }
    @Test
    void handleResolveBatchForward_digitalObjectShouldReturnDigitalObjectItems() {
        BackendV2Properties properties = new BackendV2Properties();
        properties.setRegionCode("3701");
        properties.setOrganizationCode("WXYZ");
        properties.setResolveMode("recursive");
        properties.setDrirpEnabled(true);
        properties.setDrirpPublicUrl("grpc://127.0.0.1:19090");

        DigitalObject object = new DigitalObject();
        object.setIdentifier("3701WXYZ019320260101AAAAAAAAAA");
        when(localDrisKernelFacade.getDigitalObject("3701WXYZ019320260101AAAAAAAAAA"))
                .thenReturn(object);

        DrirpResolutionService service = new DrirpResolutionService(
                localDrisKernelFacade,
                properties,
                routeForwardDao,
                directDescendantDao,
                drirpSecuritySupport,
                drirpAuditService
        );

        JsonArray identifiers = new JsonArray();
        identifiers.add("3701WXYZ019320260101AAAAAAAAAA");
        JsonObject body = new JsonObject();
        body.add("identifiers", identifiers);
        body.addProperty("resolveType", "DIGITAL_OBJECT");
        body.addProperty("ttl", 8);
        DrirpMessage request = new DrirpMessage(NodeDrirpOpcode.RESOLVE_BATCH_FORWARD, body);

        DrirpMessage response = service.handleResolveBatchForward(request);

        assertTrue(response.isSuccess());
        JsonObject out = response.getBody().getAsJsonObject();
        assertEquals("DIGITAL_OBJECT", out.get("resolveType").getAsString());
        assertEquals("3701WXYZ019320260101AAAAAAAAAA", out.getAsJsonArray("items").get(0).getAsJsonObject().getAsJsonObject("digitalObject").get("identifier").getAsString());
    }

    @Test
    void handleResolveBatchForward_resourceShouldReturnResourceItems() {
        BackendV2Properties properties = new BackendV2Properties();
        properties.setRegionCode("3701");
        properties.setOrganizationCode("WXYZ");
        properties.setResolveMode("recursive");
        properties.setDrirpEnabled(true);
        properties.setDrirpPublicUrl("grpc://127.0.0.1:19090");

        Resource resource = new Resource();
        resource.setComponentType("dataset");
        resource.setLocator("cos://bucket/object");
        when(localDrisKernelFacade.getResource("3701WXYZ019320260101AAAAAAAAAA"))
                .thenReturn(resource);

        DrirpResolutionService service = new DrirpResolutionService(
                localDrisKernelFacade,
                properties,
                routeForwardDao,
                directDescendantDao,
                drirpSecuritySupport,
                drirpAuditService
        );

        JsonArray identifiers = new JsonArray();
        identifiers.add("3701WXYZ019320260101AAAAAAAAAA");
        JsonObject body = new JsonObject();
        body.add("identifiers", identifiers);
        body.addProperty("resolveType", "RESOURCE");
        body.addProperty("ttl", 8);
        DrirpMessage request = new DrirpMessage(NodeDrirpOpcode.RESOLVE_BATCH_FORWARD, body);

        DrirpMessage response = service.handleResolveBatchForward(request);

        assertTrue(response.isSuccess());
        JsonObject out = response.getBody().getAsJsonObject();
        assertEquals("RESOURCE", out.get("resolveType").getAsString());
        JsonObject item = out.getAsJsonArray("items").get(0).getAsJsonObject();
        assertEquals("dataset", item.getAsJsonObject("resource").get("componentType").getAsString());
        assertEquals("cos://bucket/object", item.getAsJsonObject("resource").get("locator").getAsString());
    }
}
