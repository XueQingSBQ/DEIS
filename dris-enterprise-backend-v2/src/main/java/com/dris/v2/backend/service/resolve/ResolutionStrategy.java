package com.dris.v2.backend.service.resolve;

import com.dris.v2.backend.contract.DrisResponse;
import com.dris.v2.backend.model.ResolveType;

public interface ResolutionStrategy {
    <T> DrisResponse<T> resolve(String identifier, ResolveType resolveType, Class<T> dataType, ResolutionForwardHop firstHop);
}
