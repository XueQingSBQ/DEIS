package com.dris.v2.backend.control;

import com.dris.v2.backend.api.ApiResponses;
import com.dris.v2.backend.dto.ApiResponse;
import com.dris.v2.backend.dto.GlobalSearchRequest;
import com.dris.v2.backend.dto.GlobalSearchResponse;
import com.dris.v2.backend.service.DrirpSearchService;
import com.dris.v2.backend.service.FederatedSearchService;
import com.dris.v2.backend.contract.search.SearchRequest;
import com.dris.v2.backend.contract.search.SearchResult;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v2/search")
public class SearchController {
    private final FederatedSearchService service;
    private final DrirpSearchService drirpSearchService;

    public SearchController(FederatedSearchService service, DrirpSearchService drirpSearchService) {
        this.service = service;
        this.drirpSearchService = drirpSearchService;
    }

    @PostMapping
    public ResponseEntity<ApiResponse<List<SearchResult>>> search(@RequestBody SearchRequest request) {
        return ApiResponses.fromDris(service.search(request));
    }

    @PostMapping("/global")
    public ResponseEntity<ApiResponse<GlobalSearchResponse>> globalSearch(@RequestBody GlobalSearchRequest request) {
        return ApiResponses.fromDris(drirpSearchService.searchDistributed(request));
    }
}
