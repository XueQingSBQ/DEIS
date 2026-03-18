package com.dris.v2.backend.service;

import com.dris.v2.backend.config.BackendV2Properties;
import com.dris.v2.backend.config.DrirpSecuritySupport;
import com.dris.v2.backend.dao.DirectDescendantDao;
import com.dris.v2.backend.dao.RouteForwardDao;
import com.dris.v2.backend.dto.GlobalSearchRequest;
import com.dris.v2.backend.dto.GlobalSearchResponse;
import com.dris.v2.backend.model.NodeDrirpOpcode;
import com.dris.v2.backend.service.local.search.LocalSearchService;
import com.dris.v2.backend.contract.search.SearchRequest;
import com.dris.v2.backend.contract.search.SearchResult;
import com.dris.v2.backend.contract.DrisResponse;
import com.google.gson.JsonObject;
import org.bdware.drirp.model.DrirpMessage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DrirpSearchServiceTest {
    @Mock
    private LocalSearchService localSearchService;
    @Mock
    private RouteForwardDao routeForwardDao;
    @Mock
    private DirectDescendantDao directDescendantDao;
    @Mock
    private DrirpSecuritySupport drirpSecuritySupport;
    @Mock
    private DrirpAuditService drirpAuditService;

    @Test
    void searchDistributed_drirpDisabledShouldSearchLocally() {
        BackendV2Properties properties = new BackendV2Properties();
        properties.setRegionCode("3701");
        properties.setOrganizationCode("WXYZ");
        properties.setDrirpEnabled(false);
        properties.setDrirpPublicUrl("grpc://127.0.0.1:19090");

        SearchResult row = new SearchResult();
        row.setIdentifier("3701WXYZ019320260101AAAAAAAAAA");
        when(localSearchService.search(any(SearchRequest.class))).thenReturn(DrisResponse.success(Collections.singletonList(row)));

        DrirpSearchService service = new DrirpSearchService(
                localSearchService,
                properties,
                routeForwardDao,
                directDescendantDao,
                drirpSecuritySupport,
                drirpAuditService
        );

        GlobalSearchRequest request = new GlobalSearchRequest();
        request.setNaturalQuery("water data");
        request.setLimit(10);

        DrisResponse<GlobalSearchResponse> response = service.searchDistributed(request);

        assertTrue(response.isOk());
        assertEquals("3701WXYZ", response.getData().getAnchorNodeCode());
        assertEquals(1, response.getData().getItems().size());
        assertEquals("3701WXYZ", response.getData().getItems().get(0).getSource());
        verify(localSearchService).search(any(SearchRequest.class));
    }

    @Test
    void handleSearchForward_locateAnchorParentFallbackShouldDelegateToParent() {
        BackendV2Properties properties = new BackendV2Properties();
        properties.setRegionCode("1100");
        properties.setOrganizationCode("AAAA");
        properties.setResolveAllowParentFallback(true);
        properties.setParentDrirpUrl("grpc://127.0.0.1:29090");
        properties.setParentRegionCode("3700");
        properties.setParentOrganizationCode("ABCD");
        properties.setDrirpPublicUrl("grpc://127.0.0.1:19090");

        when(routeForwardDao.findByTargetCode("3701WXYZ")).thenReturn(null);
        when(directDescendantDao.findByChildCode("3701WXYZ")).thenReturn(null);

        DrirpSearchService service = new DrirpSearchService(
                localSearchService,
                properties,
                routeForwardDao,
                directDescendantDao,
                drirpSecuritySupport,
                drirpAuditService
        );

        JsonObject body = new JsonObject();
        body.addProperty("phase", "LOCATE_ANCHOR");
        body.addProperty("strategy", "ITERATIVE");
        body.addProperty("anchorNodeCode", "3701WXYZ");
        body.addProperty("ttl", 8);
        body.addProperty("traceId", "trace-search-1");
        DrirpMessage request = new DrirpMessage(NodeDrirpOpcode.SEARCH_FORWARD, body);

        DrirpMessage response = service.handleSearchForward(request);

        assertTrue(response.isSuccess());
        JsonObject out = response.getBody().getAsJsonObject();
        assertTrue(out.get("delegate").getAsBoolean());
        assertEquals("3700ABCD", out.get("nextHopCode").getAsString());
        assertEquals("grpc://127.0.0.1:29090", out.get("nextHopDrirpUrl").getAsString());
        assertEquals("trace-search-1", out.get("traceId").getAsString());
    }

    @Test
    void handleSearchForward_searchSubtreeLocalShouldReturnHits() {
        BackendV2Properties properties = new BackendV2Properties();
        properties.setRegionCode("3701");
        properties.setOrganizationCode("WXYZ");
        properties.setDrirpEnabled(true);
        properties.setDrirpPublicUrl("grpc://127.0.0.1:19090");

        SearchResult row = new SearchResult();
        row.setIdentifier("3701WXYZ019320260101AAAAAAAAAA");
        when(localSearchService.search(any(SearchRequest.class))).thenReturn(DrisResponse.success(Collections.singletonList(row)));

        DrirpSearchService service = new DrirpSearchService(
                localSearchService,
                properties,
                routeForwardDao,
                directDescendantDao,
                drirpSecuritySupport,
                drirpAuditService
        );

        JsonObject body = new JsonObject();
        body.addProperty("phase", "SEARCH_SUBTREE");
        body.addProperty("strategy", "RECURSIVE");
        body.addProperty("anchorNodeCode", "3701WXYZ");
        body.addProperty("ttl", 8);
        body.addProperty("traceId", "trace-search-2");
        JsonObject searchRequest = new JsonObject();
        searchRequest.addProperty("naturalQuery", "water data");
        searchRequest.addProperty("limit", 10);
        searchRequest.addProperty("offset", 0);
        searchRequest.addProperty("semanticWeight", 0.7);
        body.add("searchRequest", searchRequest);
        DrirpMessage request = new DrirpMessage(NodeDrirpOpcode.SEARCH_FORWARD, body);

        DrirpMessage response = service.handleSearchForward(request);

        assertTrue(response.isSuccess());
        JsonObject out = response.getBody().getAsJsonObject();
        assertEquals(1, out.getAsJsonArray("hits").size());
        assertEquals("3701WXYZ", out.getAsJsonArray("visitedNodeCodes").get(0).getAsString());
        assertEquals("trace-search-2", out.get("traceId").getAsString());
        verify(localSearchService).search(any(SearchRequest.class));
    }
}
