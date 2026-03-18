package com.dris.v2.backend.service;

import com.dris.v2.backend.config.BackendV2Properties;
import com.dris.v2.backend.config.DrirpSecuritySupport;
import com.dris.v2.backend.dao.DirectDescendantDao;
import com.dris.v2.backend.dao.RouteForwardDao;
import com.dris.v2.backend.dto.NodeRegisterRequest;
import com.dris.v2.backend.dto.NodeRegisterResponse;
import com.dris.v2.backend.model.BusinessException;
import com.google.gson.JsonObject;
import org.bdware.drirp.model.DrirpMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NodeTopologyServiceTest {
    @Mock
    private DirectDescendantDao directDescendantDao;
    @Mock
    private RouteForwardDao routeForwardDao;
    @Mock
    private DrirpSecuritySupport drirpSecuritySupport;
    @Mock
    private DrirpAuditService drirpAuditService;

    private BackendV2Properties properties;
    private NodeTopologyService service;

    @BeforeEach
    void setUp() {
        properties = new BackendV2Properties();
        properties.setNodeId("node-country");
        properties.setNodeLevel("COUNTRY");
        properties.setRegionCode("1100");
        properties.setOrganizationCode("AAAA");
        properties.setDrirpPublicUrl("grpc://127.0.0.1:29090");
        properties.setParentDrirpUrl("");
        service = new NodeTopologyService(properties, directDescendantDao, routeForwardDao, drirpSecuritySupport, drirpAuditService);
    }

    @Test
    void registerChild_shouldGenerateBootstrapConfigWhenUnique() {
        NodeRegisterRequest request = new NodeRegisterRequest();
        request.setChildNodeName("node-province");
        request.setRegionCode("3700");
        request.setOrgCode("ABCD");
        request.setChildDrirpUrl("grpc://127.0.0.1:39100");

        when(directDescendantDao.existsByCode("3700", "ABCD")).thenReturn(false);
        when(routeForwardDao.existsByCode("3700", "ABCD")).thenReturn(false);

        NodeRegisterResponse response = service.registerChild(request);

        assertEquals("PROVINCE", response.getChildLevel());
        assertEquals("node-province", response.getBootstrapConfig().getNodeName());
        assertEquals("node-country", response.getBootstrapConfig().getParentNodeName());
        verify(directDescendantDao).upsertPending("node-province", "3700", "ABCD", "PROVINCE", "grpc://127.0.0.1:39100");
    }

    @Test
    void registerChild_shouldRejectOnDuplicateCode() {
        NodeRegisterRequest request = new NodeRegisterRequest();
        request.setChildNodeName("node-province");
        request.setRegionCode("3700");
        request.setOrgCode("ABCD");
        request.setChildDrirpUrl("grpc://127.0.0.1:39100");

        when(directDescendantDao.existsByCode("3700", "ABCD")).thenReturn(true);

        assertThrows(BusinessException.class, () -> service.registerChild(request));
    }

    @Test
    void handleUniquenessCheckUp_rootNodeShouldReturnUniqueFlag() {
        when(directDescendantDao.existsByCode("3701", "WXYZ")).thenReturn(false);
        when(routeForwardDao.existsByCode("3701", "WXYZ")).thenReturn(false);

        JsonObject body = new JsonObject();
        body.addProperty("regionCode", "3701");
        body.addProperty("orgCode", "WXYZ");
        DrirpMessage request = new DrirpMessage(7101, body);

        DrirpMessage response = service.handleUniquenessCheckUp(request);

        assertTrue(response.isSuccess());
        assertTrue(response.getBody().getAsJsonObject().get("unique").getAsBoolean());
    }
}
