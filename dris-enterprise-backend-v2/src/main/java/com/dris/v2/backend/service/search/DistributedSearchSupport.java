package com.dris.v2.backend.service.search;

import com.dris.v2.backend.common.concurrent.AsyncExecutionSupport;
import com.dris.v2.backend.config.BackendV2Properties;
import com.dris.v2.backend.config.DrirpSecuritySupport;
import com.dris.v2.backend.common.drirp.SharedDrirpClientPool;
import com.dris.v2.backend.service.DrirpAuditService;
import com.dris.v2.backend.dao.DirectDescendantDao;
import com.dris.v2.backend.dao.RouteForwardDao;
import com.dris.v2.backend.dto.GlobalSearchRequest;
import com.dris.v2.backend.dto.GlobalSearchResponse;
import com.dris.v2.backend.model.DirectDescendantRecord;
import com.dris.v2.backend.model.NodeDrirpOpcode;
import com.dris.v2.backend.model.RouteForwardRecord;
import com.dris.v2.backend.model.SearchForwardError;
import com.dris.v2.backend.model.SearchForwardHop;
import com.dris.v2.backend.model.SearchForwardPhase;
import com.dris.v2.backend.model.SearchForwardRequestPayload;
import com.dris.v2.backend.model.SearchForwardResponsePayload;
import com.dris.v2.backend.model.SearchStrategy;
import com.dris.v2.backend.service.local.search.LocalSearchService;
import com.dris.v2.backend.contract.search.SearchRequest;
import com.dris.v2.backend.contract.search.SearchResult;
import com.dris.v2.backend.contract.DrisResponse;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import org.bdware.drirp.client.DrirpClientImpl;
import org.bdware.drirp.exception.DrirpClientException;
import org.bdware.drirp.model.DrirpMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

public class DistributedSearchSupport {
    private static final Logger log = LoggerFactory.getLogger(DistributedSearchSupport.class);
    private static final double GLOBAL_MIN_RETURN_SCORE = 0.18;
    private static final double GLOBAL_TOP_SCORE_RATIO = 0.60;
    private static final double GLOBAL_CLIFF_RATIO = 0.62;
    private static final double GLOBAL_DUPLICATE_BOOST_STEP = 0.01;
    private static final double GLOBAL_DUPLICATE_BOOST_CAP = 0.03;

    private final LocalSearchService localSearchService;
    private final BackendV2Properties properties;
    private final RouteForwardDao routeForwardDao;
    private final DirectDescendantDao directDescendantDao;
    private final DrirpSecuritySupport drirpSecuritySupport;
    private final DrirpAuditService drirpAuditService;
    private final Executor searchExecutor;
    private final Gson gson = new Gson();

    public DistributedSearchSupport(
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

    public DistributedSearchSupport(
            LocalSearchService localSearchService,
            BackendV2Properties properties,
            RouteForwardDao routeForwardDao,
            DirectDescendantDao directDescendantDao,
            DrirpSecuritySupport drirpSecuritySupport,
            DrirpAuditService drirpAuditService,
            Executor searchExecutor
    ) {
        this.localSearchService = localSearchService;
        this.properties = properties;
        this.routeForwardDao = routeForwardDao;
        this.directDescendantDao = directDescendantDao;
        this.drirpSecuritySupport = drirpSecuritySupport;
        this.drirpAuditService = drirpAuditService;
        this.searchExecutor = searchExecutor == null ? AsyncExecutionSupport.directExecutor() : searchExecutor;
    }

    public DrisResponse<GlobalSearchResponse> searchDistributed(GlobalSearchRequest request) {
        if (request == null) {
            return DrisResponse.failure("VALIDATION_ERROR", "request is required", null);
        }
        SearchRequest baseRequest = request.toSearchRequest();
        try {
            baseRequest.validate();
        } catch (IllegalArgumentException e) {
            return DrisResponse.failure("VALIDATION_ERROR", e.getMessage(), null);
        }

        String localCode = localNodeCode();
        String anchorNodeCode = isBlank(request.getAnchorNodeCode()) ? localCode : trim(request.getAnchorNodeCode());
        if (!isValidNodeCode(anchorNodeCode)) {
            return DrisResponse.failure("VALIDATION_ERROR", "anchorNodeCode must be 8 chars (4 digits + 4 letters)", null);
        }
        SearchStrategy strategy = SearchStrategy.parse(request.getStrategy());
        int ttl = normalizeTtl(request.getTtl());
        int maxFanout = normalizeMaxFanout(request.getMaxFanout());
        int perNodeLimit = normalizePerNodeLimit(request.getPerNodeLimit(), baseRequest.getLimit());
        String traceId = isBlank(request.getTraceId()) ? ("trace-" + System.nanoTime()) : trim(request.getTraceId());
        SearchRuntimeOptions options = new SearchRuntimeOptions(strategy, ttl, maxFanout, perNodeLimit, traceId);

        if (!properties.isDrirpEnabled()) {
            if (!localCode.equals(anchorNodeCode)) {
                return DrisResponse.failure("ROUTE_NOT_FOUND", "drirp disabled and anchor is not local", null);
            }
            SearchAggregationResult local = searchLocalOnly(baseRequest, localCode, perNodeLimit);
            if (!local.ok) {
                return DrisResponse.failure(local.errorCode, local.errorMessage, null);
            }
            GlobalSearchResponse out = buildFinalResponse(local, anchorNodeCode, baseRequest.getOffset(), baseRequest.getLimit(), traceId);
            return DrisResponse.success(out);
        }

        AnchorLocateResult anchor = strategy == SearchStrategy.ITERATIVE
                ? locateAnchorIterative(anchorNodeCode, options)
                : locateAnchorRecursive(anchorNodeCode, options);
        if (!anchor.ok) {
            return DrisResponse.failure(anchor.errorCode, anchor.errorMessage, null);
        }

        SearchAggregationResult aggregation = strategy == SearchStrategy.ITERATIVE
                ? searchSubtreeIterative(anchor, baseRequest, options)
                : searchSubtreeRecursive(anchor, baseRequest, options);
        if (!aggregation.ok) {
            return DrisResponse.failure(aggregation.errorCode, aggregation.errorMessage, null);
        }
        GlobalSearchResponse out = buildFinalResponse(
                aggregation,
                anchorNodeCode,
                baseRequest.getOffset(),
                baseRequest.getLimit(),
                traceId
        );
        return DrisResponse.success(out);
    }

    public DrirpMessage handleSearchForward(DrirpMessage request) {
        SearchForwardRequestPayload payload = toRequestPayload(request == null ? null : request.getBody());
        SearchForwardPhase phase = SearchForwardPhase.parse(payload.getPhase());
        SearchStrategy strategy = SearchStrategy.parse(payload.getStrategy());
        if (phase == SearchForwardPhase.LOCATE_ANCHOR) {
            return handleLocateAnchorForward(request, payload, strategy);
        }
        return handleSearchSubtreeForward(request, payload, strategy);
    }

    private DrirpMessage handleLocateAnchorForward(DrirpMessage request, SearchForwardRequestPayload payload, SearchStrategy strategy) {
        String anchorNodeCode = trim(payload.getAnchorNodeCode());
        if (!isValidNodeCode(anchorNodeCode)) {
            return error(request, "VALIDATION_ERROR", "anchorNodeCode is invalid");
        }
        String selfCode = localNodeCode();
        String selfUrl = resolveSelfDrirpUrl();
        List<String> visitedNodeCodes = sanitize(payload.getVisitedNodeCodes());
        List<String> visitedUrls = sanitize(payload.getVisitedUrls());
        if (contains(visitedNodeCodes, selfCode) || contains(visitedUrls, selfUrl)) {
            return error(request, "LOOP_DETECTED", "anchor locate loop detected");
        }
        visitedNodeCodes = append(visitedNodeCodes, selfCode);
        visitedUrls = append(visitedUrls, selfUrl);

        int ttl = payload.getTtl() == null ? normalizeMaxHops() : payload.getTtl().intValue();
        if (selfCode.equals(anchorNodeCode)) {
            SearchForwardResponsePayload out = new SearchForwardResponsePayload();
            out.setAnchorFound(Boolean.TRUE);
            out.setAnchorNodeCode(anchorNodeCode);
            out.setAnchorDrirpUrl(selfUrl);
            out.setTtlRemaining(Integer.valueOf(Math.max(0, ttl)));
            out.setVisitedNodeCodes(visitedNodeCodes);
            out.setVisitedUrls(visitedUrls);
            out.setTraceId(payload.getTraceId());
            return ok(request, gson.toJsonTree(out));
        }
        if (ttl <= 0) {
            return error(request, "TTL_EXCEEDED", "ttl exceeded before anchor located");
        }

        ForwardHop nextHop = resolveNextHop(anchorNodeCode);
        if (nextHop == null || isBlank(nextHop.nextHopUrl)) {
            return error(request, "ROUTE_NOT_FOUND", "route not found for anchor node");
        }
        if (contains(visitedUrls, nextHop.nextHopUrl)) {
            return error(request, "LOOP_DETECTED", "next hop already visited");
        }

        if (strategy == SearchStrategy.ITERATIVE) {
            SearchForwardResponsePayload out = new SearchForwardResponsePayload();
            out.setDelegate(Boolean.TRUE);
            out.setAnchorFound(Boolean.FALSE);
            out.setAnchorNodeCode(anchorNodeCode);
            out.setNextHopCode(nextHop.nextHopCode);
            out.setNextHopDrirpUrl(nextHop.nextHopUrl);
            out.setTtlRemaining(Integer.valueOf(ttl - 1));
            out.setVisitedNodeCodes(visitedNodeCodes);
            out.setVisitedUrls(visitedUrls);
            out.setTraceId(payload.getTraceId());
            return ok(request, gson.toJsonTree(out));
        }

        SearchForwardRequestPayload forward = copyPayload(payload);
        forward.setPhase(SearchForwardPhase.LOCATE_ANCHOR.name());
        forward.setStrategy(SearchStrategy.RECURSIVE.name());
        forward.setTtl(Integer.valueOf(ttl - 1));
        forward.setVisitedNodeCodes(visitedNodeCodes);
        forward.setVisitedUrls(visitedUrls);
        DrirpMessage response = sendForward(nextHop.nextHopUrl, request, forward);
        if (response == null) {
            return error(request, "UPSTREAM_ERROR", "upstream response is null");
        }
        if (request != null) {
            if (response.getRid() == null) {
                response.setRid(request.getRid());
            }
            if (response.getCode() == 0) {
                response.setCode(request.getCode());
            }
        }
        return response;
    }

    private DrirpMessage handleSearchSubtreeForward(DrirpMessage request, SearchForwardRequestPayload payload, SearchStrategy strategy) {
        SearchRequest incomingRequest = payload.getSearchRequest();
        if (incomingRequest == null) {
            return error(request, "VALIDATION_ERROR", "searchRequest is required");
        }
        try {
            incomingRequest.validate();
        } catch (IllegalArgumentException e) {
            return error(request, "VALIDATION_ERROR", e.getMessage());
        }
        int perNodeLimit = normalizePerNodeLimit(payload.getPerNodeLimit(), incomingRequest.getLimit());
        int maxFanout = normalizeMaxFanout(payload.getMaxFanout());
        int ttl = payload.getTtl() == null ? normalizeMaxHops() : payload.getTtl().intValue();
        String selfCode = localNodeCode();
        String selfUrl = resolveSelfDrirpUrl();

        List<String> visitedNodeCodes = sanitize(payload.getVisitedNodeCodes());
        List<String> visitedUrls = sanitize(payload.getVisitedUrls());
        if (contains(visitedNodeCodes, selfCode) || contains(visitedUrls, selfUrl)) {
            return error(request, "LOOP_DETECTED", "search subtree loop detected");
        }
        visitedNodeCodes = append(visitedNodeCodes, selfCode);
        visitedUrls = append(visitedUrls, selfUrl);

        DrisResponse<List<SearchResult>> local = searchLocal(buildPerNodeSearchRequest(incomingRequest, perNodeLimit), selfCode);
        if (local == null || !local.isOk()) {
            return error(
                    request,
                    local == null ? "UPSTREAM_ERROR" : blankIfNull(local.getCode(), "UPSTREAM_ERROR"),
                    local == null ? "local search failed" : blankIfNull(local.getMessage(), "local search failed")
            );
        }
        List<SearchResult> localHits = local.getData() == null ? new ArrayList<SearchResult>() : local.getData();

        if (strategy == SearchStrategy.ITERATIVE) {
            SearchForwardResponsePayload out = new SearchForwardResponsePayload();
            out.setHits(localHits);
            out.setPartial(Boolean.FALSE);
            out.setTtlRemaining(Integer.valueOf(Math.max(0, ttl - 1)));
            out.setVisitedNodeCodes(visitedNodeCodes);
            out.setVisitedUrls(visitedUrls);
            out.setTraceId(payload.getTraceId());
            if (ttl > 0) {
                out.setNextHopList(listActiveChildrenHops(maxFanout, visitedNodeCodes, visitedUrls));
            }
            return ok(request, gson.toJsonTree(out));
        }

        SearchForwardResponsePayload out = new SearchForwardResponsePayload();
        out.setHits(new ArrayList<SearchResult>(localHits));
        out.setPartial(Boolean.FALSE);
        out.setVisitedNodeCodes(visitedNodeCodes);
        out.setVisitedUrls(visitedUrls);
        out.setTraceId(payload.getTraceId());
        out.setTtlRemaining(Integer.valueOf(Math.max(0, ttl - 1)));

        if (ttl <= 0) {
            return ok(request, gson.toJsonTree(out));
        }

        List<SearchForwardHop> children = listActiveChildrenHops(maxFanout, visitedNodeCodes, visitedUrls);
        if (children.isEmpty()) {
            return ok(request, gson.toJsonTree(out));
        }

        List<SearchChildTask> childTasks = new ArrayList<SearchChildTask>(children.size());
        List<String> baseVisitedNodeCodes = new ArrayList<String>(out.getVisitedNodeCodes());
        List<String> baseVisitedUrls = new ArrayList<String>(out.getVisitedUrls());
        for (SearchForwardHop child : children) {
            SearchForwardRequestPayload forward = copyPayload(payload);
            forward.setPhase(SearchForwardPhase.SEARCH_SUBTREE.name());
            forward.setStrategy(SearchStrategy.RECURSIVE.name());
            forward.setTtl(Integer.valueOf(ttl - 1));
            forward.setPerNodeLimit(Integer.valueOf(perNodeLimit));
            forward.setMaxFanout(Integer.valueOf(maxFanout));
            forward.setVisitedNodeCodes(new ArrayList<String>(baseVisitedNodeCodes));
            forward.setVisitedUrls(new ArrayList<String>(baseVisitedUrls));
            childTasks.add(new SearchChildTask(
                    child,
                    AsyncExecutionSupport.supplyAsync(searchExecutor, () -> forwardSearchChild(child, request, forward))
            ));
        }
        for (SearchChildTask childTask : childTasks) {
            SearchChildResult childResult = awaitChildSearchResult(childTask);
            if (!childResult.success) {
                out.setPartial(Boolean.TRUE);
                out.getErrors().add(new SearchForwardError(
                        childTask.child.getNodeCode(),
                        childResult.errorCode,
                        childResult.errorMessage
                ));
                continue;
            }
            SearchForwardResponsePayload childPayload = childResult.payload;
            out.getHits().addAll(nullToList(childPayload.getHits()));
            out.setVisitedNodeCodes(mergeUnique(out.getVisitedNodeCodes(), childPayload.getVisitedNodeCodes()));
            out.setVisitedUrls(mergeUnique(out.getVisitedUrls(), childPayload.getVisitedUrls()));
            if (Boolean.TRUE.equals(childPayload.getPartial())) {
                out.setPartial(Boolean.TRUE);
            }
            out.getErrors().addAll(nullToErrorList(childPayload.getErrors()));
        }
        return ok(request, gson.toJsonTree(out));
    }

    private AnchorLocateResult locateAnchorRecursive(String anchorNodeCode, SearchRuntimeOptions options) {
        String selfCode = localNodeCode();
        String selfUrl = resolveSelfDrirpUrl();
        if (selfCode.equals(anchorNodeCode)) {
            AnchorLocateResult local = new AnchorLocateResult();
            local.ok = true;
            local.anchorNodeCode = selfCode;
            local.anchorDrirpUrl = selfUrl;
            local.visitedNodeCodes = append(Collections.<String>emptyList(), selfCode);
            local.visitedUrls = append(Collections.<String>emptyList(), selfUrl);
            return local;
        }
        ForwardHop firstHop = resolveNextHop(anchorNodeCode);
        if (firstHop == null || isBlank(firstHop.nextHopUrl)) {
            return AnchorLocateResult.failure("ROUTE_NOT_FOUND", "route not found for anchor node");
        }
        SearchForwardRequestPayload payload = new SearchForwardRequestPayload();
        payload.setPhase(SearchForwardPhase.LOCATE_ANCHOR.name());
        payload.setStrategy(SearchStrategy.RECURSIVE.name());
        payload.setAnchorNodeCode(anchorNodeCode);
        payload.setEntryNodeCode(selfCode);
        payload.setTtl(Integer.valueOf(options.ttl));
        payload.setTraceId(options.traceId);
        payload.setVisitedNodeCodes(append(Collections.<String>emptyList(), selfCode));
        payload.setVisitedUrls(append(Collections.<String>emptyList(), selfUrl));
        DrirpMessage response = sendForward(firstHop.nextHopUrl, null, payload);
        if (response == null) {
            return AnchorLocateResult.failure("UPSTREAM_ERROR", "anchor locate upstream response is null");
        }
        if (!response.isSuccess()) {
            return AnchorLocateResult.failure(
                    blankIfNull(response.getErrorCode(), "UPSTREAM_ERROR"),
                    blankIfNull(response.getErrorMessage(), "anchor locate failed")
            );
        }
        SearchForwardResponsePayload body = toResponsePayload(response.getBody());
        if (!Boolean.TRUE.equals(body.getAnchorFound()) || isBlank(body.getAnchorDrirpUrl())) {
            return AnchorLocateResult.failure("PROTOCOL_ERROR", "anchor locate response invalid");
        }
        AnchorLocateResult out = new AnchorLocateResult();
        out.ok = true;
        out.anchorNodeCode = isBlank(body.getAnchorNodeCode()) ? anchorNodeCode : body.getAnchorNodeCode();
        out.anchorDrirpUrl = body.getAnchorDrirpUrl();
        out.visitedNodeCodes = sanitize(body.getVisitedNodeCodes());
        out.visitedUrls = sanitize(body.getVisitedUrls());
        return out;
    }

    private AnchorLocateResult locateAnchorIterative(String anchorNodeCode, SearchRuntimeOptions options) {
        String selfCode = localNodeCode();
        String selfUrl = resolveSelfDrirpUrl();
        if (selfCode.equals(anchorNodeCode)) {
            AnchorLocateResult local = new AnchorLocateResult();
            local.ok = true;
            local.anchorNodeCode = selfCode;
            local.anchorDrirpUrl = selfUrl;
            local.visitedNodeCodes = append(Collections.<String>emptyList(), selfCode);
            local.visitedUrls = append(Collections.<String>emptyList(), selfUrl);
            return local;
        }
        ForwardHop current = resolveNextHop(anchorNodeCode);
        if (current == null || isBlank(current.nextHopUrl)) {
            return AnchorLocateResult.failure("ROUTE_NOT_FOUND", "route not found for anchor node");
        }
        int ttl = options.ttl;
        List<String> visitedNodeCodes = append(Collections.<String>emptyList(), selfCode);
        List<String> visitedUrls = append(Collections.<String>emptyList(), selfUrl);
        while (ttl > 0) {
            SearchForwardRequestPayload payload = new SearchForwardRequestPayload();
            payload.setPhase(SearchForwardPhase.LOCATE_ANCHOR.name());
            payload.setStrategy(SearchStrategy.ITERATIVE.name());
            payload.setAnchorNodeCode(anchorNodeCode);
            payload.setEntryNodeCode(selfCode);
            payload.setTtl(Integer.valueOf(ttl));
            payload.setTraceId(options.traceId);
            payload.setVisitedNodeCodes(visitedNodeCodes);
            payload.setVisitedUrls(visitedUrls);

            DrirpMessage response = sendForward(current.nextHopUrl, null, payload);
            if (response == null) {
                return AnchorLocateResult.failure("UPSTREAM_ERROR", "anchor locate upstream response is null");
            }
            if (!response.isSuccess()) {
                return AnchorLocateResult.failure(
                        blankIfNull(response.getErrorCode(), "UPSTREAM_ERROR"),
                        blankIfNull(response.getErrorMessage(), "anchor locate failed")
                );
            }
            SearchForwardResponsePayload body = toResponsePayload(response.getBody());
            visitedNodeCodes = mergeUnique(visitedNodeCodes, body.getVisitedNodeCodes());
            visitedUrls = mergeUnique(visitedUrls, body.getVisitedUrls());

            if (Boolean.TRUE.equals(body.getAnchorFound()) && !isBlank(body.getAnchorDrirpUrl())) {
                AnchorLocateResult out = new AnchorLocateResult();
                out.ok = true;
                out.anchorNodeCode = isBlank(body.getAnchorNodeCode()) ? anchorNodeCode : body.getAnchorNodeCode();
                out.anchorDrirpUrl = body.getAnchorDrirpUrl();
                out.visitedNodeCodes = visitedNodeCodes;
                out.visitedUrls = visitedUrls;
                return out;
            }
            if (!Boolean.TRUE.equals(body.getDelegate()) || isBlank(body.getNextHopDrirpUrl())) {
                return AnchorLocateResult.failure("PROTOCOL_ERROR", "anchor locate delegate response invalid");
            }
            if (contains(visitedUrls, body.getNextHopDrirpUrl())) {
                return AnchorLocateResult.failure("LOOP_DETECTED", "next hop already visited while locating anchor");
            }
            ttl = body.getTtlRemaining() == null ? ttl - 1 : body.getTtlRemaining().intValue();
            current = new ForwardHop(body.getNextHopCode(), trim(body.getNextHopDrirpUrl()));
        }
        return AnchorLocateResult.failure("TTL_EXCEEDED", "ttl exceeded before anchor located");
    }

    private SearchAggregationResult searchSubtreeRecursive(
            AnchorLocateResult anchor,
            SearchRequest baseRequest,
            SearchRuntimeOptions options
    ) {
        SearchForwardRequestPayload payload = new SearchForwardRequestPayload();
        payload.setPhase(SearchForwardPhase.SEARCH_SUBTREE.name());
        payload.setStrategy(SearchStrategy.RECURSIVE.name());
        payload.setAnchorNodeCode(anchor.anchorNodeCode);
        payload.setEntryNodeCode(localNodeCode());
        payload.setSearchRequest(buildPerNodeSearchRequest(baseRequest, options.perNodeLimit));
        payload.setTtl(Integer.valueOf(options.ttl));
        payload.setMaxFanout(Integer.valueOf(options.maxFanout));
        payload.setPerNodeLimit(Integer.valueOf(options.perNodeLimit));
        payload.setTraceId(options.traceId);
        // Anchor locate path should not constrain subtree search. Otherwise, when a child node
        // initiates a search anchored at its parent, the parent treats that child as already
        // visited and skips searching back into the child's subtree.
        payload.setVisitedNodeCodes(new ArrayList<String>());
        payload.setVisitedUrls(new ArrayList<String>());

        DrirpMessage response = sendForward(anchor.anchorDrirpUrl, null, payload);
        if (response == null) {
            return SearchAggregationResult.failure("UPSTREAM_ERROR", "search subtree response is null");
        }
        if (!response.isSuccess()) {
            return SearchAggregationResult.failure(
                    blankIfNull(response.getErrorCode(), "UPSTREAM_ERROR"),
                    blankIfNull(response.getErrorMessage(), "search subtree failed")
            );
        }
        SearchForwardResponsePayload body = toResponsePayload(response.getBody());
        SearchAggregationResult out = new SearchAggregationResult();
        out.ok = true;
        out.hits.addAll(nullToList(body.getHits()));
        out.partial = Boolean.TRUE.equals(body.getPartial());
        out.visitedNodeCodes = mergeUnique(anchor.visitedNodeCodes, body.getVisitedNodeCodes());
        out.visitedUrls = mergeUnique(anchor.visitedUrls, body.getVisitedUrls());
        for (SearchForwardError error : nullToErrorList(body.getErrors())) {
            if (!isBlank(error.getNodeCode())) {
                out.failedNodes.add(error.getNodeCode());
            }
        }
        return out;
    }

    private SearchAggregationResult searchSubtreeIterative(
            AnchorLocateResult anchor,
            SearchRequest baseRequest,
            SearchRuntimeOptions options
    ) {
        SearchAggregationResult out = new SearchAggregationResult();
        out.ok = true;
        out.visitedNodeCodes = new ArrayList<String>();
        out.visitedUrls = new ArrayList<String>();

        List<QueueEntry> queue = new ArrayList<QueueEntry>();
        LinkedHashSet<String> scheduledUrls = new LinkedHashSet<String>();
        queue.add(new QueueEntry(anchor.anchorNodeCode, anchor.anchorDrirpUrl, options.ttl));
        if (!isBlank(anchor.anchorDrirpUrl)) {
            scheduledUrls.add(anchor.anchorDrirpUrl);
        }

        while (!queue.isEmpty()) {
            List<QueueEntry> wave = drainIterativeWave(queue);
            List<IterativeNodeTask> tasks = new ArrayList<IterativeNodeTask>(wave.size());
            List<String> baseVisitedNodeCodes = new ArrayList<String>(out.visitedNodeCodes);
            List<String> baseVisitedUrls = new ArrayList<String>(out.visitedUrls);

            for (QueueEntry current : wave) {
                if (current == null || current.ttl < 0 || isBlank(current.drirpUrl)) {
                    continue;
                }
                SearchForwardRequestPayload payload = buildIterativeSearchPayload(
                        anchor,
                        baseRequest,
                        options,
                        current,
                        baseVisitedNodeCodes,
                        baseVisitedUrls
                );
                tasks.add(new IterativeNodeTask(
                        current,
                        AsyncExecutionSupport.supplyAsync(searchExecutor, () -> forwardIterativeNode(current, payload))
                ));
            }

            for (IterativeNodeTask task : tasks) {
                IterativeNodeResult result = awaitIterativeNodeResult(task);
                if (!result.success) {
                    out.partial = true;
                    out.failedNodes.add(isBlank(task.entry.nodeCode) ? task.entry.drirpUrl : task.entry.nodeCode);
                    continue;
                }
                SearchForwardResponsePayload body = result.payload;
                out.hits.addAll(nullToList(body.getHits()));
                out.visitedNodeCodes = mergeUnique(out.visitedNodeCodes, body.getVisitedNodeCodes());
                out.visitedUrls = mergeUnique(out.visitedUrls, body.getVisitedUrls());
                if (Boolean.TRUE.equals(body.getPartial())) {
                    out.partial = true;
                }
                for (SearchForwardError error : nullToErrorList(body.getErrors())) {
                    if (!isBlank(error.getNodeCode())) {
                        out.failedNodes.add(error.getNodeCode());
                        out.partial = true;
                    }
                }
                if (task.entry.ttl <= 0) {
                    continue;
                }
                enqueueIterativeNextHops(queue, scheduledUrls, body.getNextHopList(), task.entry.ttl - 1, options.maxFanout);
            }
        }
        return out;
    }

    private List<QueueEntry> drainIterativeWave(List<QueueEntry> queue) {
        List<QueueEntry> wave = new ArrayList<QueueEntry>(queue);
        queue.clear();
        return wave;
    }

    private SearchForwardRequestPayload buildIterativeSearchPayload(
            AnchorLocateResult anchor,
            SearchRequest baseRequest,
            SearchRuntimeOptions options,
            QueueEntry current,
            List<String> visitedNodeCodes,
            List<String> visitedUrls
    ) {
        SearchForwardRequestPayload payload = new SearchForwardRequestPayload();
        payload.setPhase(SearchForwardPhase.SEARCH_SUBTREE.name());
        payload.setStrategy(SearchStrategy.ITERATIVE.name());
        payload.setAnchorNodeCode(anchor.anchorNodeCode);
        payload.setEntryNodeCode(localNodeCode());
        payload.setSearchRequest(buildPerNodeSearchRequest(baseRequest, options.perNodeLimit));
        payload.setTtl(Integer.valueOf(current.ttl));
        payload.setMaxFanout(Integer.valueOf(options.maxFanout));
        payload.setPerNodeLimit(Integer.valueOf(options.perNodeLimit));
        payload.setTraceId(options.traceId);
        payload.setVisitedNodeCodes(new ArrayList<String>(visitedNodeCodes));
        payload.setVisitedUrls(new ArrayList<String>(visitedUrls));
        return payload;
    }

    private IterativeNodeResult forwardIterativeNode(QueueEntry current, SearchForwardRequestPayload payload) {
        DrirpMessage response = sendForward(current.drirpUrl, null, payload);
        if (response == null || !response.isSuccess()) {
            return IterativeNodeResult.failure();
        }
        return IterativeNodeResult.success(toResponsePayload(response.getBody()));
    }

    private IterativeNodeResult awaitIterativeNodeResult(IterativeNodeTask task) {
        try {
            return AsyncExecutionSupport.await(task.future, "iterative search node");
        } catch (RuntimeException exception) {
            return IterativeNodeResult.failure();
        }
    }

    private void enqueueIterativeNextHops(
            List<QueueEntry> queue,
            LinkedHashSet<String> scheduledUrls,
            List<SearchForwardHop> nextHops,
            int nextTtl,
            int maxFanout
    ) {
        int accepted = 0;
        for (SearchForwardHop hop : nullToHopList(nextHops)) {
            if (hop == null || isBlank(hop.getDrirpUrl())) {
                continue;
            }
            String url = trim(hop.getDrirpUrl());
            if (scheduledUrls.contains(url)) {
                continue;
            }
            scheduledUrls.add(url);
            queue.add(new QueueEntry(trim(hop.getNodeCode()), url, nextTtl));
            accepted++;
            if (accepted >= maxFanout) {
                break;
            }
        }
    }

    private SearchChildResult forwardSearchChild(
            SearchForwardHop child,
            DrirpMessage request,
            SearchForwardRequestPayload forward
    ) {
        DrirpMessage childResponse = sendForward(child.getDrirpUrl(), request, forward);
        if (childResponse == null) {
            return SearchChildResult.failure("UPSTREAM_ERROR", "null response");
        }
        if (!childResponse.isSuccess()) {
            return SearchChildResult.failure(
                    blankIfNull(childResponse.getErrorCode(), "UPSTREAM_ERROR"),
                    blankIfNull(childResponse.getErrorMessage(), "upstream error")
            );
        }
        return SearchChildResult.success(toResponsePayload(childResponse.getBody()));
    }

    private SearchChildResult awaitChildSearchResult(SearchChildTask task) {
        try {
            return AsyncExecutionSupport.await(task.future, "search child forward");
        } catch (RuntimeException exception) {
            return SearchChildResult.failure(
                    "INTERNAL_ERROR",
                    blankIfNull(exception.getMessage(), "child search failed")
            );
        }
    }

    private SearchAggregationResult searchLocalOnly(SearchRequest request, String localNodeCode, int perNodeLimit) {
        DrisResponse<List<SearchResult>> response = searchLocal(buildPerNodeSearchRequest(request, perNodeLimit), localNodeCode);
        if (response == null || !response.isOk()) {
            return SearchAggregationResult.failure(
                    response == null ? "UPSTREAM_ERROR" : blankIfNull(response.getCode(), "UPSTREAM_ERROR"),
                    response == null ? "local search failed" : blankIfNull(response.getMessage(), "local search failed")
            );
        }
        SearchAggregationResult out = new SearchAggregationResult();
        out.ok = true;
        out.hits.addAll(nullToList(response.getData()));
        out.visitedNodeCodes.add(localNodeCode);
        out.visitedUrls.add(resolveSelfDrirpUrl());
        return out;
    }

    private DrisResponse<List<SearchResult>> searchLocal(SearchRequest searchRequest, String sourceNodeCode) {
        DrisResponse<List<SearchResult>> response = localSearchService.search(searchRequest);
        if (response == null || !response.isOk() || response.getData() == null) {
            return response;
        }
        List<SearchResult> normalized = new ArrayList<SearchResult>();
        for (SearchResult row : response.getData()) {
            if (row == null || isBlank(row.getIdentifier())) {
                continue;
            }
            SearchResult copy = copyResult(row);
            copy.setSource(sourceNodeCode);
            normalized.add(copy);
        }
        return DrisResponse.success(normalized);
    }

    private GlobalSearchResponse buildFinalResponse(
            SearchAggregationResult aggregation,
            String anchorNodeCode,
            int offset,
            int limit,
            String traceId
    ) {
        int safeOffset = Math.max(0, offset);
        int safeLimit = Math.max(1, limit);
        int required = safeOffset + safeLimit;
        List<SearchResult> merged = mergeByBestLocalScore(aggregation.hits);
        List<SearchResult> pruned = pruneMergedResults(merged, required);
        SearchResultSupport.assignDisplayRanks(pruned, 1);
        int from = Math.min(safeOffset, pruned.size());
        int to = Math.min(from + safeLimit, pruned.size());
        GlobalSearchResponse out = new GlobalSearchResponse();
        out.setItems(new ArrayList<SearchResult>(pruned.subList(from, to)));
        out.setPartial(aggregation.partial);
        out.setAnchorNodeCode(anchorNodeCode);
        out.setVisitedNodeCodes(aggregation.visitedNodeCodes);
        out.setFailedNodes(uniqueStrings(aggregation.failedNodes));
        out.setTraceId(traceId);
        return out;
    }

    private List<SearchResult> mergeByBestLocalScore(List<SearchResult> hits) {
        if (hits == null || hits.isEmpty()) {
            return new ArrayList<SearchResult>();
        }

        LinkedHashMap<String, RankAccumulator> merged = new LinkedHashMap<String, RankAccumulator>();
        for (SearchResult hit : hits) {
            if (hit == null || isBlank(hit.getIdentifier())) {
                continue;
            }
            String id = trim(hit.getIdentifier());
            RankAccumulator acc = merged.get(id);
            if (acc == null) {
                acc = new RankAccumulator(copyResult(hit));
                merged.put(id, acc);
            } else {
                mergeRepresentative(acc, hit);
            }
            double nodeFinalScore = safeScore(hit.getFinalScore());
            double localScore = safeScore(hit.getLocalScore());
            if (nodeFinalScore <= 0.0) {
                nodeFinalScore = localScore;
            }
            if (localScore <= 0.0) {
                localScore = nodeFinalScore;
            }
            acc.bestNodeFinalScore = Math.max(acc.bestNodeFinalScore, nodeFinalScore);
            acc.bestLocalScore = Math.max(acc.bestLocalScore, localScore);
            acc.bestOriginalFinalScore = Math.max(acc.bestOriginalFinalScore, nodeFinalScore);
            acc.bestKeywordScore = Math.max(acc.bestKeywordScore, safeScore(hit.getKeywordScore()));
            acc.bestSemanticScore = Math.max(acc.bestSemanticScore, safeScore(hit.getSemanticScore()));
            acc.sources.add(normalizeSource(hit.getSource()));
        }

        List<SearchResult> out = new ArrayList<SearchResult>();
        for (RankAccumulator acc : merged.values()) {
            acc.result.setKeywordScore(acc.bestKeywordScore);
            acc.result.setSemanticScore(acc.bestSemanticScore);
            acc.result.setLocalScore(acc.bestLocalScore);
            acc.result.setFusionScore(computeDuplicateBoost(acc));
            acc.result.setFinalScore(computeGlobalScore(acc));
            out.add(acc.result);
        }
        out.sort(new Comparator<SearchResult>() {
            @Override
            public int compare(SearchResult a, SearchResult b) {
                RankAccumulator accA = merged.get(trim(a.getIdentifier()));
                RankAccumulator accB = merged.get(trim(b.getIdentifier()));
                int c = Double.compare(safeScore(b.getFinalScore()), safeScore(a.getFinalScore()));
                if (c != 0) {
                    return c;
                }
                c = Integer.compare(sourceCount(accB), sourceCount(accA));
                if (c != 0) {
                    return c;
                }
                c = Double.compare(bestOriginalScore(accB), bestOriginalScore(accA));
                if (c != 0) {
                    return c;
                }
                c = Double.compare(safeScore(b.getSemanticScore()), safeScore(a.getSemanticScore()));
                if (c != 0) {
                    return c;
                }
                c = Double.compare(safeScore(b.getKeywordScore()), safeScore(a.getKeywordScore()));
                if (c != 0) {
                    return c;
                }
                return trim(a.getIdentifier()).compareTo(trim(b.getIdentifier()));
            }
        });
        return out;
    }

    private void mergeRepresentative(RankAccumulator acc, SearchResult candidate) {
        if (acc == null || candidate == null) {
            return;
        }
        if (compareByNodeScoreDesc(candidate, acc.result) < 0) {
            SearchResult promoted = copyResult(candidate);
            fillMissing(promoted, acc.result);
            acc.result = promoted;
        } else {
            fillMissing(acc.result, candidate);
        }
    }

    private int compareByNodeScoreDesc(SearchResult a, SearchResult b) {
        return SearchResultSupport.compareByScoreDesc(a, b);
    }

    private List<SearchResult> pruneMergedResults(List<SearchResult> merged, int required) {
        List<SearchResult> out = new ArrayList<SearchResult>();
        if (merged == null || merged.isEmpty()) {
            return out;
        }
        int safeRequired = Math.max(1, required);
        double topScore = safeScore(merged.get(0).getFinalScore());
        double minAllowed = Math.max(GLOBAL_MIN_RETURN_SCORE, topScore * GLOBAL_TOP_SCORE_RATIO);
        double previousScore = -1.0;
        for (SearchResult result : merged) {
            if (result == null) {
                continue;
            }
            double score = safeScore(result.getFinalScore());
            if (score < minAllowed) {
                break;
            }
            if (previousScore > 0.0 && (score / previousScore) < GLOBAL_CLIFF_RATIO) {
                break;
            }
            out.add(result);
            previousScore = score;
            if (out.size() >= safeRequired) {
                break;
            }
        }
        if (out.isEmpty() && !merged.isEmpty()) {
            out.add(merged.get(0));
        }
        return out;
    }

    private String normalizeSource(String source) {
        return isBlank(source) ? "local" : trim(source);
    }

    private double safeScore(double score) {
        return SearchResultSupport.safeScore(score);
    }

    private int sourceCount(RankAccumulator acc) {
        return acc == null ? 0 : acc.sources.size();
    }

    private double bestOriginalScore(RankAccumulator acc) {
        return acc == null ? 0.0 : acc.bestOriginalFinalScore;
    }

    private double computeGlobalScore(RankAccumulator acc) {
        if (acc == null) {
            return 0.0;
        }
        return acc.bestNodeFinalScore + computeDuplicateBoost(acc);
    }

    private double computeDuplicateBoost(RankAccumulator acc) {
        if (acc == null) {
            return 0.0;
        }
        int duplicateCount = Math.max(0, sourceCount(acc) - 1);
        return Math.min(GLOBAL_DUPLICATE_BOOST_CAP, duplicateCount * GLOBAL_DUPLICATE_BOOST_STEP);
    }

    private SearchRequest buildPerNodeSearchRequest(SearchRequest base, int perNodeLimit) {
        SearchRequest out = new SearchRequest();
        out.setNaturalQuery(base.getNaturalQuery());
        out.setKeywords(base.getKeywords());
        out.setFields(base.getFields());
        out.setFilters(base.getFilters());
        out.setMode(base.getMode());
        out.setSemanticWeight(base.getSemanticWeight());
        out.setOffset(0);
        out.setLimit(perNodeLimit);
        return out;
    }

    private List<SearchForwardHop> listActiveChildrenHops(int maxFanout, List<String> visitedNodeCodes, List<String> visitedUrls) {
        List<DirectDescendantRecord> children = directDescendantDao.listActiveChildren();
        List<SearchForwardHop> out = new ArrayList<SearchForwardHop>();
        if (children == null || children.isEmpty()) {
            return out;
        }
        for (DirectDescendantRecord child : children) {
            if (child == null || isBlank(child.getChildCode()) || isBlank(child.getChildDrirpUrl())) {
                continue;
            }
            String childCode = trim(child.getChildCode());
            String childUrl = trim(child.getChildDrirpUrl());
            if (contains(visitedNodeCodes, childCode) || contains(visitedUrls, childUrl)) {
                continue;
            }
            out.add(new SearchForwardHop(childCode, childUrl));
            if (out.size() >= maxFanout) {
                break;
            }
        }
        return out;
    }

    private ForwardHop resolveNextHop(String targetCode) {
        if (isBlank(targetCode)) {
            return null;
        }
        RouteForwardRecord route = routeForwardDao.findByTargetCode(targetCode);
        if (route != null && !isBlank(route.getNextHopDrirpUrl())) {
            return new ForwardHop(route.getNextHopCode(), trim(route.getNextHopDrirpUrl()));
        }
        DirectDescendantRecord direct = directDescendantDao.findByChildCode(targetCode);
        if (direct != null
                && "ACTIVE".equalsIgnoreCase(direct.getRelationStatus())
                && !isBlank(direct.getChildDrirpUrl())) {
            return new ForwardHop(targetCode, trim(direct.getChildDrirpUrl()));
        }
        if (properties.isResolveAllowParentFallback() && !isBlank(properties.getParentDrirpUrl())) {
            return new ForwardHop(resolveParentNodeCode(), trim(properties.getParentDrirpUrl()));
        }
        return null;
    }

    private DrirpMessage sendForward(String nextHopUrl, DrirpMessage sourceRequest, SearchForwardRequestPayload payload) {
        long startedAt = System.currentTimeMillis();
        DrirpClientImpl client = null;
        String effectiveNextHopUrl = trim(nextHopUrl);
        long timeoutMs = normalizeTimeoutMs();
        String traceId = sourceRequest == null ? null : sourceRequest.getTraceId();
        boolean success = false;
        int status = 502;
        String errorMessage = "";
        try {
            client = SharedDrirpClientPool.acquire(effectiveNextHopUrl, timeoutMs, drirpSecuritySupport);
            DrirpMessage request = new DrirpMessage(NodeDrirpOpcode.SEARCH_FORWARD, gson.toJsonTree(payload));
            request.setTs(Long.valueOf(System.currentTimeMillis()));
            request.setRid(String.valueOf(System.nanoTime()));
            request.setVersion("1.0");
            if (sourceRequest != null) {
                request.setTraceId(sourceRequest.getTraceId());
            } else if (!isBlank(payload.getTraceId())) {
                request.setTraceId(payload.getTraceId());
            }
            DrirpMessage response = client.send(request);
            traceId = isBlank(traceId) ? (response == null ? request.getTraceId() : response.getTraceId()) : traceId;
            success = response != null && response.isSuccess();
            status = success ? 200 : 502;
            if (!success) {
                errorMessage = response == null
                        ? "upstream response is null"
                        : blankIfNull(response.getErrorMessage(), blankIfNull(response.getErrorCode(), "upstream error"));
            }
            return response;
        } catch (DrirpClientException e) {
            SharedDrirpClientPool.invalidate(effectiveNextHopUrl, timeoutMs, client);
            errorMessage = blankIfNull(e.getMessage(), "drirp forward failed");
            log.warn("search forward failed, nextHopUrl={}, err={}", effectiveNextHopUrl, e.getMessage());
            DrirpMessage error = DrirpMessage.error(
                    NodeDrirpOpcode.SEARCH_FORWARD,
                    sourceRequest == null ? null : sourceRequest.getRid(),
                    "drirp forward failed: " + e.getMessage()
            );
            error.setErrorCode("UPSTREAM_ERROR");
            error.setOk(Boolean.FALSE);
            return error;
        } finally {
            drirpAuditService.record(
                    "SEARCH_FORWARD_OUT",
                    traceId,
                    "",
                    effectiveNextHopUrl,
                    status,
                    System.currentTimeMillis() - startedAt,
                    success,
                    errorMessage
            );
        }
    }

    private SearchForwardRequestPayload toRequestPayload(JsonElement body) {
        if (body == null || body.isJsonNull()) {
            return new SearchForwardRequestPayload();
        }
        try {
            SearchForwardRequestPayload payload = gson.fromJson(body, SearchForwardRequestPayload.class);
            return payload == null ? new SearchForwardRequestPayload() : payload;
        } catch (Exception ignored) {
            return new SearchForwardRequestPayload();
        }
    }

    private SearchForwardResponsePayload toResponsePayload(JsonElement body) {
        if (body == null || body.isJsonNull()) {
            return new SearchForwardResponsePayload();
        }
        try {
            SearchForwardResponsePayload payload = gson.fromJson(body, SearchForwardResponsePayload.class);
            return payload == null ? new SearchForwardResponsePayload() : payload;
        } catch (Exception ignored) {
            return new SearchForwardResponsePayload();
        }
    }

    private SearchForwardRequestPayload copyPayload(SearchForwardRequestPayload source) {
        SearchForwardRequestPayload out = new SearchForwardRequestPayload();
        if (source == null) {
            return out;
        }
        out.setPhase(source.getPhase());
        out.setStrategy(source.getStrategy());
        out.setAnchorNodeCode(source.getAnchorNodeCode());
        out.setEntryNodeCode(source.getEntryNodeCode());
        out.setSearchRequest(source.getSearchRequest());
        out.setTtl(source.getTtl());
        out.setMaxFanout(source.getMaxFanout());
        out.setPerNodeLimit(source.getPerNodeLimit());
        out.setVisitedNodeCodes(source.getVisitedNodeCodes());
        out.setVisitedUrls(source.getVisitedUrls());
        out.setTraceId(source.getTraceId());
        return out;
    }

    private SearchResult copyResult(SearchResult source) {
        SearchResult out = SearchResultSupport.copy(source);
        return out == null ? new SearchResult() : out;
    }

    private void fillMissing(SearchResult target, SearchResult source) {
        SearchResultSupport.fillMissing(target, source);
    }

    private String localNodeCode() {
        return trim(properties.getRegionCode()) + trim(properties.getOrganizationCode());
    }

    private String resolveParentNodeCode() {
        String parentRegionCode = trim(properties.getParentRegionCode());
        String parentOrganizationCode = trim(properties.getParentOrganizationCode());
        if (parentRegionCode.length() == 4 && parentOrganizationCode.length() == 4) {
            return parentRegionCode + parentOrganizationCode;
        }
        return "";
    }

    private String resolveSelfDrirpUrl() {
        if (!isBlank(properties.getDrirpPublicUrl())) {
            return trim(properties.getDrirpPublicUrl());
        }
        return "grpc://127.0.0.1:" + properties.getDrirpPort();
    }

    private int normalizeMaxHops() {
        return properties.getResolveMaxHops() <= 0 ? 8 : properties.getResolveMaxHops();
    }

    private long normalizeTimeoutMs() {
        return properties.getResolveTimeoutMs() <= 0 ? 5000L : properties.getResolveTimeoutMs();
    }

    private int normalizeTtl(Integer ttl) {
        if (ttl == null || ttl.intValue() <= 0) {
            return normalizeMaxHops();
        }
        return Math.min(64, ttl.intValue());
    }

    private int normalizeMaxFanout(Integer maxFanout) {
        if (maxFanout == null || maxFanout.intValue() <= 0) {
            return 16;
        }
        return Math.min(64, maxFanout.intValue());
    }

    private int normalizePerNodeLimit(Integer perNodeLimit, int fallbackLimit) {
        int fallback = fallbackLimit <= 0 ? 20 : fallbackLimit;
        if (perNodeLimit == null || perNodeLimit.intValue() <= 0) {
            return Math.min(200, fallback);
        }
        return Math.min(200, perNodeLimit.intValue());
    }

    private static boolean isValidNodeCode(String nodeCode) {
        if (nodeCode == null || nodeCode.length() != 8) {
            return false;
        }
        String region = nodeCode.substring(0, 4);
        String org = nodeCode.substring(4);
        return region.matches("^[0-9]{4}$") && org.matches("^[A-Z]{4}$");
    }

    private static List<String> sanitize(List<String> values) {
        if (values == null || values.isEmpty()) {
            return new ArrayList<String>();
        }
        List<String> out = new ArrayList<String>();
        for (String value : values) {
            if (!isBlank(value)) {
                out.add(trim(value));
            }
        }
        return out;
    }

    private static List<String> append(List<String> source, String value) {
        List<String> out = new ArrayList<String>();
        if (source != null) {
            out.addAll(source);
        }
        if (!isBlank(value)) {
            out.add(trim(value));
        }
        return out;
    }

    private static boolean contains(List<String> values, String value) {
        if (values == null || isBlank(value)) {
            return false;
        }
        for (String item : values) {
            if (trim(value).equals(trim(item))) {
                return true;
            }
        }
        return false;
    }

    private static List<String> mergeUnique(List<String> left, List<String> right) {
        LinkedHashSet<String> merged = new LinkedHashSet<String>();
        if (left != null) {
            for (String item : left) {
                if (!isBlank(item)) {
                    merged.add(trim(item));
                }
            }
        }
        if (right != null) {
            for (String item : right) {
                if (!isBlank(item)) {
                    merged.add(trim(item));
                }
            }
        }
        return new ArrayList<String>(merged);
    }

    private static List<String> removeValue(List<String> values, String target) {
        List<String> out = new ArrayList<String>();
        if (values == null || values.isEmpty()) {
            return out;
        }
        String normalized = trim(target);
        for (String item : values) {
            if (isBlank(item)) {
                continue;
            }
            if (!trim(item).equals(normalized)) {
                out.add(trim(item));
            }
        }
        return out;
    }

    private static List<String> uniqueStrings(List<String> values) {
        return mergeUnique(values, null);
    }

    private static List<SearchResult> nullToList(List<SearchResult> values) {
        return values == null ? new ArrayList<SearchResult>() : values;
    }

    private static List<SearchForwardError> nullToErrorList(List<SearchForwardError> values) {
        return values == null ? new ArrayList<SearchForwardError>() : values;
    }

    private static List<SearchForwardHop> nullToHopList(List<SearchForwardHop> values) {
        return values == null ? new ArrayList<SearchForwardHop>() : values;
    }

    private static String trim(String value) {
        return value == null ? "" : value.trim();
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private static String blankIfNull(String value, String fallback) {
        if (value == null || value.trim().isEmpty()) {
            return fallback;
        }
        return value;
    }

    private static DrirpMessage ok(DrirpMessage request, JsonElement body) {
        DrirpMessage response = DrirpMessage.ok(
                request == null ? NodeDrirpOpcode.SEARCH_FORWARD : request.getCode(),
                request == null ? null : request.getRid(),
                body
        );
        response.setOk(Boolean.TRUE);
        if (request != null) {
            response.setTraceId(request.getTraceId());
            response.setVersion(request.getVersion());
        }
        return response;
    }

    private static DrirpMessage error(DrirpMessage request, String errorCode, String message) {
        DrirpMessage response = DrirpMessage.error(
                request == null ? NodeDrirpOpcode.SEARCH_FORWARD : request.getCode(),
                request == null ? null : request.getRid(),
                message
        );
        response.setErrorCode(blankIfNull(errorCode, "ERROR"));
        response.setErrorMessage(blankIfNull(message, "error"));
        response.setOk(Boolean.FALSE);
        if (request != null) {
            response.setTraceId(request.getTraceId());
            response.setVersion(request.getVersion());
        }
        return response;
    }

    private static final class ForwardHop {
        private final String nextHopCode;
        private final String nextHopUrl;

        private ForwardHop(String nextHopCode, String nextHopUrl) {
            this.nextHopCode = nextHopCode == null ? "" : nextHopCode;
            this.nextHopUrl = nextHopUrl;
        }
    }

    private static final class QueueEntry {
        private final String nodeCode;
        private final String drirpUrl;
        private final int ttl;

        private QueueEntry(String nodeCode, String drirpUrl, int ttl) {
            this.nodeCode = nodeCode;
            this.drirpUrl = drirpUrl;
            this.ttl = ttl;
        }
    }

    private static final class IterativeNodeTask {
        private final QueueEntry entry;
        private final CompletableFuture<IterativeNodeResult> future;

        private IterativeNodeTask(QueueEntry entry, CompletableFuture<IterativeNodeResult> future) {
            this.entry = entry;
            this.future = future;
        }
    }

    private static final class IterativeNodeResult {
        private final boolean success;
        private final SearchForwardResponsePayload payload;

        private IterativeNodeResult(boolean success, SearchForwardResponsePayload payload) {
            this.success = success;
            this.payload = payload;
        }

        private static IterativeNodeResult success(SearchForwardResponsePayload payload) {
            return new IterativeNodeResult(true, payload);
        }

        private static IterativeNodeResult failure() {
            return new IterativeNodeResult(false, null);
        }
    }

    private static final class RankAccumulator {
        private SearchResult result;
        private double bestNodeFinalScore = 0.0;
        private double bestOriginalFinalScore = 0.0;
        private double bestKeywordScore = 0.0;
        private double bestSemanticScore = 0.0;
        private double bestLocalScore = 0.0;
        private final LinkedHashSet<String> sources = new LinkedHashSet<String>();

        private RankAccumulator(SearchResult result) {
            this.result = result;
        }
    }

    private static final class SearchChildTask {
        private final SearchForwardHop child;
        private final CompletableFuture<SearchChildResult> future;

        private SearchChildTask(SearchForwardHop child, CompletableFuture<SearchChildResult> future) {
            this.child = child;
            this.future = future;
        }
    }

    private static final class SearchChildResult {
        private final boolean success;
        private final String errorCode;
        private final String errorMessage;
        private final SearchForwardResponsePayload payload;

        private SearchChildResult(boolean success, String errorCode, String errorMessage, SearchForwardResponsePayload payload) {
            this.success = success;
            this.errorCode = errorCode;
            this.errorMessage = errorMessage;
            this.payload = payload;
        }

        private static SearchChildResult success(SearchForwardResponsePayload payload) {
            return new SearchChildResult(true, "OK", "ok", payload);
        }

        private static SearchChildResult failure(String errorCode, String errorMessage) {
            return new SearchChildResult(false, errorCode, errorMessage, null);
        }
    }

    private static final class SearchRuntimeOptions {
        private final SearchStrategy strategy;
        private final int ttl;
        private final int maxFanout;
        private final int perNodeLimit;
        private final String traceId;

        private SearchRuntimeOptions(SearchStrategy strategy, int ttl, int maxFanout, int perNodeLimit, String traceId) {
            this.strategy = strategy;
            this.ttl = ttl;
            this.maxFanout = maxFanout;
            this.perNodeLimit = perNodeLimit;
            this.traceId = traceId;
        }
    }

    private static final class AnchorLocateResult {
        private boolean ok;
        private String anchorNodeCode;
        private String anchorDrirpUrl;
        private List<String> visitedNodeCodes = new ArrayList<String>();
        private List<String> visitedUrls = new ArrayList<String>();
        private String errorCode;
        private String errorMessage;

        private static AnchorLocateResult failure(String errorCode, String errorMessage) {
            AnchorLocateResult result = new AnchorLocateResult();
            result.ok = false;
            result.errorCode = errorCode;
            result.errorMessage = errorMessage;
            return result;
        }
    }

    private static final class SearchAggregationResult {
        private boolean ok;
        private final List<SearchResult> hits = new ArrayList<SearchResult>();
        private boolean partial;
        private List<String> visitedNodeCodes = new ArrayList<String>();
        private List<String> visitedUrls = new ArrayList<String>();
        private final List<String> failedNodes = new ArrayList<String>();
        private String errorCode;
        private String errorMessage;

        private static SearchAggregationResult failure(String errorCode, String errorMessage) {
            SearchAggregationResult result = new SearchAggregationResult();
            result.ok = false;
            result.errorCode = errorCode;
            result.errorMessage = errorMessage;
            return result;
        }
    }
}
