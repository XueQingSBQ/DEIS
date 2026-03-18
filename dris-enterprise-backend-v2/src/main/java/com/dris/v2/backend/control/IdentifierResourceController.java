package com.dris.v2.backend.control;

import com.dris.v2.backend.api.ApiResponses;
import com.dris.v2.backend.dto.ApiResponse;
import com.dris.v2.backend.dto.BatchResolveResourceResponse;
import com.dris.v2.backend.dto.IdentifierComponentVersionPageResponse;
import com.dris.v2.backend.dto.IdentifierComponentVersionView;
import com.dris.v2.backend.service.IdentifierComponentVersionQueryService;
import com.dris.v2.backend.service.IdentifierResourceService;
import com.dris.sdk.next.domain.Resource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v2/identifiers")
public class IdentifierResourceController {
    private final IdentifierResourceService service;
    private final IdentifierComponentVersionQueryService versionQueryService;

    public IdentifierResourceController(
            IdentifierResourceService service,
            IdentifierComponentVersionQueryService versionQueryService
    ) {
        this.service = service;
        this.versionQueryService = versionQueryService;
    }

    @GetMapping("/{identifier}/component")
    public ResponseEntity<ApiResponse<Resource>> getComponent(@PathVariable("identifier") String identifier) {
        return ApiResponses.fromDris(service.getComponent(identifier));
    }

    @GetMapping("/{identifier}/component/versions")
    public ResponseEntity<ApiResponse<IdentifierComponentVersionPageResponse>> componentVersions(
            @PathVariable("identifier") String identifier,
            @RequestParam(value = "page", required = false) Integer page,
            @RequestParam(value = "size", required = false) Integer size
    ) {
        return ApiResponses.ok(versionQueryService.queryPage(identifier, page, size));
    }

    @GetMapping("/{identifier}/component/versions/{versionNo}")
    public ResponseEntity<ApiResponse<IdentifierComponentVersionView>> componentVersion(
            @PathVariable("identifier") String identifier,
            @PathVariable("versionNo") Long versionNo
    ) {
        return ApiResponses.ok(versionQueryService.getOne(identifier, versionNo));
    }

    @PostMapping(value = "/resolve/batch/component/distributed", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ApiResponse<BatchResolveResourceResponse>> resolveComponentBatchDistributed(@RequestBody List<String> identifiers) {
        return ApiResponses.fromDris(service.resolveResourceBatchDistributed(identifiers));
    }

    @PutMapping("/{identifier}/component")
    public ResponseEntity<ApiResponse<Boolean>> upsertComponent(
            @PathVariable("identifier") String identifier,
            @RequestBody Resource resource,
            @RequestParam(name = "forceReplaceType", required = false, defaultValue = "false") boolean forceReplaceType
    ) {
        return ApiResponses.fromDris(service.upsertComponent(identifier, resource, forceReplaceType));
    }

    @DeleteMapping("/{identifier}/component")
    public ResponseEntity<ApiResponse<Boolean>> removeComponent(@PathVariable("identifier") String identifier) {
        return ApiResponses.fromDris(service.removeComponent(identifier));
    }
}

