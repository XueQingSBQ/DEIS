package com.dris.v2.backend.control;

import com.dris.v2.backend.dto.NodeBootstrapConfig;
import com.dris.v2.backend.dto.NodeRegisterResponse;
import com.dris.v2.backend.model.BusinessException;
import com.dris.v2.backend.service.NodeTopologyService;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = NodeTopologyController.class)
class NodeTopologyControllerTest {
    @Autowired
    private MockMvc mockMvc;
    @MockBean
    private NodeTopologyService nodeTopologyService;

    @Test
    void register_shouldReturnOk() throws Exception {
        NodeRegisterResponse response = new NodeRegisterResponse();
        response.setChildNodeName("node-city-01");
        response.setChildLevel("CITY");
        response.setRegionCode("3701");
        response.setOrgCode("ABCD");
        response.setRelationStatus("PENDING");
        response.setHealthStatus("UNKNOWN");
        NodeBootstrapConfig config = new NodeBootstrapConfig();
        config.setNodeName("node-city-01");
        config.setNodeLevel("CITY");
        config.setRegionCode("3701");
        config.setOrgCode("ABCD");
        config.setParentNodeName("node-province-01");
        config.setParentDrirpUrl("grpc://127.0.0.1:29190");
        response.setBootstrapConfig(config);

        when(nodeTopologyService.registerChild(any())).thenReturn(response);

        mockMvc.perform(post("/api/v2/node/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"childNodeName\":\"node-city-01\",\"regionCode\":\"3701\",\"orgCode\":\"ABCD\",\"childDrirpUrl\":\"grpc://127.0.0.1:39200\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ok").value(true))
                .andExpect(jsonPath("$.data.childNodeName").value("node-city-01"))
                .andExpect(jsonPath("$.data.bootstrapConfig.parentNodeName").value("node-province-01"));
    }

    @Test
    void register_conflict_shouldReturn409() throws Exception {
        when(nodeTopologyService.registerChild(any()))
                .thenThrow(new BusinessException("CONFLICT", "node code already exists in global topology", HttpStatus.CONFLICT));

        mockMvc.perform(post("/api/v2/node/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"childNodeName\":\"node-city-01\",\"regionCode\":\"3701\",\"orgCode\":\"ABCD\",\"childDrirpUrl\":\"grpc://127.0.0.1:39200\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.ok").value(false))
                .andExpect(jsonPath("$.code").value("CONFLICT"));
    }

    @Test
    void routes_shouldReturnOk() throws Exception {
        when(nodeTopologyService.listRouteForward()).thenReturn(Collections.emptyList());

        mockMvc.perform(get("/api/v2/node/routes"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ok").value(true));
    }
}
