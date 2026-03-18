package com.dris.v2.backend.service;

import com.dris.v2.backend.common.concurrent.AsyncExecutionSupport;
import com.dris.v2.backend.config.BackendV2Properties;
import com.dris.v2.backend.config.DrirpSecuritySupport;
import com.dris.v2.backend.config.WorkloadExecutorConfiguration;
import com.dris.v2.backend.contract.DrisResponse;
import com.dris.v2.backend.dao.DirectDescendantDao;
import com.dris.v2.backend.dao.RouteForwardDao;
import com.dris.v2.backend.dto.GlobalSearchRequest;
import com.dris.v2.backend.dto.GlobalSearchResponse;
import com.dris.v2.backend.service.local.search.LocalSearchService;
import com.dris.v2.backend.service.search.DistributedSearchSupport;
import org.bdware.drirp.model.DrirpMessage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.util.concurrent.Executor;

@Service
public class DrirpSearchService {
    private final DistributedSearchSupport support;

    public DrirpSearchService(
            LocalSearchService localSearchService,
            BackendV2Properties properties,
            RouteForwardDao routeForwardDao,
            DirectDescendantDao directDescendantDao,
            DrirpSecuritySupport drirpSecuritySupport,
            DrirpAuditService drirpAuditService
    ) {
        this(
                localSearchService,
                properties,
                routeForwardDao,
                directDescendantDao,
                drirpSecuritySupport,
                drirpAuditService,
                AsyncExecutionSupport.directExecutor()
        );
    }

    @Autowired
    public DrirpSearchService(
            LocalSearchService localSearchService,
            BackendV2Properties properties,
            RouteForwardDao routeForwardDao,
            DirectDescendantDao directDescendantDao,
            DrirpSecuritySupport drirpSecuritySupport,
            DrirpAuditService drirpAuditService,
            @Qualifier(WorkloadExecutorConfiguration.SEARCH_EXECUTOR) Executor searchExecutor
    ) {
        this.support = new DistributedSearchSupport(
                localSearchService,
                properties,
                routeForwardDao,
                directDescendantDao,
                drirpSecuritySupport,
                drirpAuditService,
                searchExecutor
        );
    }

    public DrisResponse<GlobalSearchResponse> searchDistributed(GlobalSearchRequest request) {
        return support.searchDistributed(request);
    }

    public DrirpMessage handleSearchForward(DrirpMessage request) {
        return support.handleSearchForward(request);
    }
}