package com.dris.v2.backend.service.resolve;

import com.dris.v2.backend.contract.DrisResponse;
import com.dris.v2.backend.model.ResolveForwardRequestPayload;
import com.dris.v2.backend.model.ResolveType;
import org.bdware.drirp.model.DrirpMessage;

import java.util.Collections;

public class RecursiveResolutionStrategy implements ResolutionStrategy {
    private final ResolutionSupport support;

    public RecursiveResolutionStrategy(ResolutionSupport support) {
        this.support = support;
    }

    @Override
    public <T> DrisResponse<T> resolve(String identifier, ResolveType resolveType, Class<T> dataType, ResolutionForwardHop firstHop) {
        ResolveForwardRequestPayload payload = new ResolveForwardRequestPayload();
        payload.setIdentifier(identifier);
        payload.setResolveType(resolveType.name());
        payload.setTtl(Integer.valueOf(support.normalizeMaxHops()));
        payload.setVisitedUrls(support.append(Collections.<String>emptyList(), support.resolveSelfDrirpUrl()));

        DrirpMessage response = support.sendForward(firstHop.getNextHopUrl(), null, payload);
        return support.parseResolveResponse(response, resolveType, dataType);
    }
}
