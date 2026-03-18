package com.dris.v2.backend.control;

import com.dris.v2.backend.api.ApiResponses;
import com.dris.v2.backend.contract.registration.IdentifierRegistrationView;
import com.dris.v2.backend.contract.registration.IdentifierStatusUpdateRequest;
import com.dris.v2.backend.dto.ApiResponse;
import com.dris.v2.backend.dto.BatchResolveDigitalObjectResponse;
import com.dris.v2.backend.dto.BatchResolveMetadataResponse;
import com.dris.sdk.next.domain.DigitalObject;
import com.dris.v2.backend.service.IdentifierCoreService;
import com.dris.sdk.next.domain.IdentifierMetadata;
import com.dris.v2.backend.contract.registration.RegistrationRequest;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v2/identifiers")
public class IdentifierCoreController {
    private final IdentifierCoreService service;

    public IdentifierCoreController(IdentifierCoreService service) {
        this.service = service;
    }

    @PostMapping("/register")
    public ResponseEntity<ApiResponse<String>> register(@RequestBody RegistrationRequest request) {
        return ApiResponses.fromDris(service.register(request));
    }

    @PostMapping("/register/detail")
    public ResponseEntity<ApiResponse<IdentifierRegistrationView>> registerDetail(@RequestBody RegistrationRequest request) {
        return ApiResponses.fromDris(service.registerDetail(request));
    }

    @GetMapping("/{identifier}/metadata")
    public ResponseEntity<ApiResponse<IdentifierMetadata>> metadata(@PathVariable("identifier") String identifier) {
        return ApiResponses.fromDris(service.resolveMetadata(identifier));
    }

    @PostMapping("/register/batch")
    public ResponseEntity<ApiResponse<List<String>>> registerBatch(@RequestBody List<RegistrationRequest> requests) {
        return ApiResponses.fromDris(service.registerBatch(requests));
    }

    @GetMapping("/{identifier}/validate")
    public ResponseEntity<ApiResponse<Boolean>> validate(@PathVariable("identifier") String identifier) {
        return ApiResponses.fromDris(service.validateIdentifier(identifier));
    }

    @GetMapping("/{identifier}/exists")
    public ResponseEntity<ApiResponse<Boolean>> exists(@PathVariable("identifier") String identifier) {
        return ApiResponses.fromDris(service.existsIdentifier(identifier));
    }

    @PostMapping(value = "/resolve/batch", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ApiResponse<List<IdentifierMetadata>>> resolveBatch(@RequestBody List<String> identifiers) {
        return ApiResponses.fromDris(service.resolveMetadataBatch(identifiers));
    }

    @PostMapping(value = "/resolve/batch/distributed", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ApiResponse<BatchResolveMetadataResponse>> resolveBatchDistributed(@RequestBody List<String> identifiers) {
        return ApiResponses.fromDris(service.resolveMetadataBatchDistributed(identifiers));
    }

    @PostMapping(value = "/resolve/batch/object/distributed", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ApiResponse<BatchResolveDigitalObjectResponse>> resolveObjectBatchDistributed(@RequestBody List<String> identifiers) {
        return ApiResponses.fromDris(service.resolveDigitalObjectBatchDistributed(identifiers));
    }

    @GetMapping("/{identifier}/object")
    public ResponseEntity<ApiResponse<DigitalObject>> object(@PathVariable("identifier") String identifier) {
        return ApiResponses.fromDris(service.getDigitalObject(identifier));
    }

    @PutMapping("/{identifier}/metadata")
    public ResponseEntity<ApiResponse<Boolean>> updateMetadata(
            @PathVariable("identifier") String identifier,
            @RequestBody IdentifierMetadata metadata
    ) {
        return ApiResponses.fromDris(service.updateIdentifier(identifier, metadata));
    }

    @PatchMapping("/{identifier}/metadata")
    public ResponseEntity<ApiResponse<Boolean>> patchMetadata(
            @PathVariable("identifier") String identifier,
            @RequestBody Map<String, String> fields
    ) {
        return ApiResponses.fromDris(service.partialUpdateIdentifier(identifier, fields));
    }

    @PatchMapping("/{identifier}/status")
    public ResponseEntity<ApiResponse<Boolean>> updateStatus(
            @PathVariable("identifier") String identifier,
            @RequestBody IdentifierStatusUpdateRequest request
    ) {
        return ApiResponses.fromDris(service.updateIdentifierStatus(identifier, request));
    }

    @DeleteMapping("/{identifier}")
    public ResponseEntity<ApiResponse<Boolean>> delete(@PathVariable("identifier") String identifier) {
        return ApiResponses.fromDris(service.deleteIdentifier(identifier));
    }
}

