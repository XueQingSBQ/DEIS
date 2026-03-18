package org.bdware.drirp.filter;

import org.bdware.drirp.auth.DrirpAuthProvider;
import org.bdware.drirp.model.DrirpErrorCodes;
import org.bdware.drirp.model.DrirpMessage;

public class DrirpAuthFilter implements DrirpRequestFilter {
    private final DrirpAuthProvider authProvider;
    private final boolean allowAnonymous;

    public DrirpAuthFilter(DrirpAuthProvider authProvider) {
        this(authProvider, false);
    }

    public DrirpAuthFilter(DrirpAuthProvider authProvider, boolean allowAnonymous) {
        this.authProvider = authProvider;
        this.allowAnonymous = allowAnonymous;
    }

    @Override
    public DrirpMessage beforeHandle(DrirpMessage request) {
        if (authProvider == null) {
            return null;
        }
        if (allowAnonymous && (request == null || !request.hasAuth())) {
            return null;
        }
        if (!authProvider.verify(request)) {
            DrirpMessage error = DrirpMessage.error(request == null ? 0 : request.getCode(),
                    request == null ? null : request.getRid(),
                    "auth failed");
            error.setErrorCode(DrirpErrorCodes.AUTH_FAILED);
            return error;
        }
        return null;
    }
}


