package org.bdware.drirp.server;

import org.bdware.drirp.auth.DrirpAuthProvider;
import org.bdware.drirp.DrirpConfig;
import org.bdware.drirp.filter.DrirpRequestFilter;
import org.bdware.drirp.model.DrirpErrorCodes;
import org.bdware.drirp.model.DrirpMessage;
import org.bdware.drirp.opcode.DrirpOpcode;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

public class DrirpDispatcher {
    private final Map<Integer, DrirpRequestHandler> handlers = new ConcurrentHashMap<>();
    private volatile DrirpRequestHandler fallbackHandler;
    private final List<DrirpRequestFilter> filters = new CopyOnWriteArrayList<>();
    private volatile DrirpAuthProvider responseSigner;
    private final Map<String, IdempotencyEntry> idempotencyCache = new ConcurrentHashMap<String, IdempotencyEntry>();
    private volatile boolean idempotencyEnabled = true;
    private volatile long idempotencyTtlMs = 10 * 60 * 1000L;
    private volatile int idempotencyMaxEntries = 100000;

    public DrirpDispatcher register(int code, DrirpRequestHandler handler) {
        if (handler != null) {
            handlers.put(code, handler);
        }
        return this;
    }

    public DrirpDispatcher setFallbackHandler(DrirpRequestHandler handler) {
        this.fallbackHandler = handler;
        return this;
    }

    public DrirpDispatcher addFilter(DrirpRequestFilter filter) {
        if (filter != null) {
            filters.add(filter);
        }
        return this;
    }

    public DrirpDispatcher setResponseSigner(DrirpAuthProvider signer) {
        this.responseSigner = signer;
        return this;
    }

    public DrirpDispatcher setIdempotencyEnabled(boolean enabled) {
        this.idempotencyEnabled = enabled;
        return this;
    }

    public DrirpDispatcher setIdempotencyTtlMs(long ttlMs) {
        if (ttlMs > 0) {
            this.idempotencyTtlMs = ttlMs;
        }
        return this;
    }

    public DrirpDispatcher setIdempotencyMaxEntries(int maxEntries) {
        if (maxEntries > 0) {
            this.idempotencyMaxEntries = maxEntries;
        }
        return this;
    }

    public DrirpMessage handle(DrirpMessage request) {
        if (request == null) {
            return signIfNeeded(error(0, null, "empty request", DrirpErrorCodes.VALIDATION_ERROR));
        }
        int code = request.getCode();
        if (code <= 0) {
            return signIfNeeded(error(code, request.getRid(), "invalid opcode", DrirpErrorCodes.VALIDATION_ERROR));
        }
        for (DrirpRequestFilter filter : filters) {
            DrirpMessage early = filter.beforeHandle(request);
            if (early != null) {
                return signIfNeeded(early);
            }
        }
        String idempotencyCacheKey = buildIdempotencyKey(request);
        if (idempotencyCacheKey != null) {
            DrirpMessage cached = lookupIdempotency(idempotencyCacheKey);
            if (cached != null) {
                return cached;
            }
        }
        DrirpRequestHandler handler = handlers.get(code);
        if (handler == null) {
            if (fallbackHandler == null) {
                return signIfNeeded(error(code, request.getRid(), "unsupported opcode: " + code, DrirpErrorCodes.UNSUPPORTED_OPCODE));
            }
            handler = fallbackHandler;
        }
        try {
            DrirpMessage response = handler.handle(request);
            if (response == null) {
                return signIfNeeded(error(code, request.getRid(), "empty response", DrirpErrorCodes.INTERNAL_ERROR));
            }
            if (response.getCode() == 0) {
                response.setCode(code);
            }
            if (response.getRid() == null) {
                response.setRid(request.getRid());
            }
            if (response.getTraceId() == null) {
                response.setTraceId(request.getTraceId());
            }
            if (response.getVersion() == null) {
                response.setVersion(request.getVersion());
            }
            DrirpMessage signed = signIfNeeded(response);
            if (idempotencyCacheKey != null && signed != null && signed.isSuccess()) {
                rememberIdempotency(idempotencyCacheKey, signed);
            }
            return signed;
        } catch (Exception e) {
            return signIfNeeded(error(code, request.getRid(), "handler error: " + e.getMessage(), DrirpErrorCodes.HANDLER_ERROR));
        }
    }

    private DrirpMessage signIfNeeded(DrirpMessage response) {
        if (response != null) {
            response.ensureProtocolDefaults(DrirpConfig.DEFAULT_PROTOCOL_VERSION);
        }
        if (responseSigner != null && response != null) {
            responseSigner.sign(response);
        }
        return response;
    }

    private DrirpMessage error(int code, String rid, String message, String errorCode) {
        DrirpMessage error = DrirpMessage.error(code, rid, message);
        error.setErrorCode(errorCode);
        return error;
    }

    private String buildIdempotencyKey(DrirpMessage request) {
        if (!idempotencyEnabled || request == null || !request.hasIdempotencyKey() || !isMutationOpcode(request.getCode())) {
            return null;
        }
        return request.getCode() + ":" + request.getIdempotencyKey().trim();
    }

    private DrirpMessage lookupIdempotency(String key) {
        if (key == null) {
            return null;
        }
        cleanupIdempotency();
        IdempotencyEntry entry = idempotencyCache.get(key);
        if (entry == null) {
            return null;
        }
        if (System.currentTimeMillis() - entry.createdAt > idempotencyTtlMs) {
            idempotencyCache.remove(key, entry);
            return null;
        }
        return entry.response.copy();
    }

    private void rememberIdempotency(String key, DrirpMessage response) {
        if (key == null || response == null) {
            return;
        }
        cleanupIdempotency();
        idempotencyCache.put(key, new IdempotencyEntry(response.copy(), System.currentTimeMillis()));
    }

    private void cleanupIdempotency() {
        if (idempotencyCache.isEmpty()) {
            return;
        }
        long now = System.currentTimeMillis();
        for (Map.Entry<String, IdempotencyEntry> entry : idempotencyCache.entrySet()) {
            if (now - entry.getValue().createdAt > idempotencyTtlMs) {
                idempotencyCache.remove(entry.getKey(), entry.getValue());
            }
        }
        if (idempotencyCache.size() > idempotencyMaxEntries) {
            int toRemove = idempotencyCache.size() - idempotencyMaxEntries;
            for (String key : idempotencyCache.keySet()) {
                if (toRemove <= 0) {
                    break;
                }
                if (idempotencyCache.remove(key) != null) {
                    toRemove--;
                }
            }
        }
    }

    private boolean isMutationOpcode(int code) {
        return code == DrirpOpcode.REGISTER || code == DrirpOpcode.UPDATE || code == DrirpOpcode.DELETE;
    }

    private static final class IdempotencyEntry {
        private final DrirpMessage response;
        private final long createdAt;

        private IdempotencyEntry(DrirpMessage response, long createdAt) {
            this.response = response;
            this.createdAt = createdAt;
        }
    }
}


