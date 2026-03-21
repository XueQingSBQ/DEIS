package com.dris.v2.backend.control;

import com.dris.v2.backend.api.ApiResponses;
import com.dris.v2.backend.dto.ApiResponse;
import com.dris.v2.backend.dto.DirectDescendantView;
import com.dris.v2.backend.dto.AdjacentNodeStatusView;
import com.dris.v2.backend.dto.NodeRegisterRequest;
import com.dris.v2.backend.dto.NodeRegisterResponse;
import com.dris.v2.backend.dto.NodeShutdownNotifyResponse;
import com.dris.v2.backend.dto.RouteForwardView;
import com.dris.v2.backend.service.NodeTopologyService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.validation.Valid;
import java.util.List;

@RestController
@RequestMapping("/api/v2/node")
public class NodeTopologyController {
    private final NodeTopologyService nodeTopologyService;

    public NodeTopologyController(NodeTopologyService nodeTopologyService) {
        this.nodeTopologyService = nodeTopologyService;
    }

    @PostMapping("/register")
    public ResponseEntity<ApiResponse<NodeRegisterResponse>> register(@Valid @RequestBody NodeRegisterRequest request) {
        return ApiResponses.ok(nodeTopologyService.registerChild(request));
    }

    @GetMapping("/direct-descendants")
    public ResponseEntity<ApiResponse<List<DirectDescendantView>>> directDescendants() {
        return ApiResponses.ok(nodeTopologyService.listDirectDescendants());
    }

    @GetMapping({"/adjacent/status", "/direct-descendants/status"})
    public ResponseEntity<ApiResponse<List<AdjacentNodeStatusView>>> adjacentNodeStatuses() {
        return ApiResponses.ok(nodeTopologyService.listAdjacentNodeStatuses());
    }

    @GetMapping("/routes")
    public ResponseEntity<ApiResponse<List<RouteForwardView>>> routes() {
        return ApiResponses.ok(nodeTopologyService.listRouteForward());
    }

    @PostMapping("/shutdown")
    public ResponseEntity<ApiResponse<NodeShutdownNotifyResponse>> shutdownNotify() {
        return ApiResponses.ok(nodeTopologyService.notifyConnectedNodesShutdown());
    }
}

