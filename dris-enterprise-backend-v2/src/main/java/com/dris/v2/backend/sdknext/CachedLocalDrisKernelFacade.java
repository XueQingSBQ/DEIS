package com.dris.v2.backend.sdknext;
import com.dris.v2.backend.common.cache.LocalTtlCache;
import com.dris.v2.backend.config.BackendV2Properties;
import com.dris.v2.backend.contract.registration.RegistrationRequest;
import com.dris.sdk.next.domain.DigitalObject;
import com.dris.sdk.next.domain.IdentifierMetadata;
import com.dris.sdk.next.domain.Resource;
import com.dris.sdk.next.enumtype.ObjectStatus;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
@Primary
@Service
public class CachedLocalDrisKernelFacade implements LocalDrisKernelFacade {
    private final LocalDrisKernelFacade delegate;
    private final LocalTtlCache<String, Boolean> existsCache;
    private final LocalTtlCache<String, IdentifierMetadata> metadataCache;
    private final LocalTtlCache<String, Resource> resourceCache;
    private final LocalTtlCache<String, DigitalObject> digitalObjectCache;
    public CachedLocalDrisKernelFacade(
            @Qualifier("delegateLocalDrisKernelFacade") LocalDrisKernelFacade delegate,
            BackendV2Properties properties
    ) {
        this.delegate = delegate;
        BackendV2Properties.KernelReadCacheProperties cache = properties == null
                ? null
                : properties.getKernelReadCache();
        boolean enabled = cache == null || cache.isEnabled();
        long ttlMs = cache == null ? 15000L : cache.getTtlMs();
        int maxEntries = cache == null ? 2000 : cache.getMaxEntries();
        this.existsCache = new LocalTtlCache<String, Boolean>(enabled, ttlMs, maxEntries, new Function<Boolean, Boolean>() {
            @Override
            public Boolean apply(Boolean value) {
                return value;
            }
        });
        this.metadataCache = new LocalTtlCache<String, IdentifierMetadata>(enabled, ttlMs, maxEntries, new Function<IdentifierMetadata, IdentifierMetadata>() {
            @Override
            public IdentifierMetadata apply(IdentifierMetadata value) {
                return value == null ? null : value.copy();
            }
        });
        this.resourceCache = new LocalTtlCache<String, Resource>(enabled, ttlMs, maxEntries, new Function<Resource, Resource>() {
            @Override
            public Resource apply(Resource value) {
                return value == null ? null : value.copy();
            }
        });
        this.digitalObjectCache = new LocalTtlCache<String, DigitalObject>(enabled, ttlMs, maxEntries, new Function<DigitalObject, DigitalObject>() {
            @Override
            public DigitalObject apply(DigitalObject value) {
                return value == null ? null : value.copy();
            }
        });
    }
    @Override
    public String register(RegistrationRequest request) {
        String identifier = delegate.register(request);
        invalidateAll(identifier);
        return identifier;
    }
    @Override
    public List<String> registerBatch(List<RegistrationRequest> requests) {
        List<String> identifiers = delegate.registerBatch(requests);
        if (identifiers != null) {
            for (String identifier : identifiers) {
                invalidateAll(identifier);
            }
        }
        return identifiers;
    }
    @Override
    public boolean validateIdentifier(String identifier) {
        return delegate.validateIdentifier(identifier);
    }
    @Override
    public boolean existsIdentifier(String identifier) {
        String key = normalize(identifier);
        if (key == null) {
            return delegate.existsIdentifier(identifier);
        }
        Boolean cached = existsCache.get(key);
        if (cached != null) {
            return cached.booleanValue();
        }
        boolean exists = delegate.existsIdentifier(key);
        existsCache.put(key, Boolean.valueOf(exists));
        return exists;
    }
    @Override
    public IdentifierMetadata getMetadata(String identifier) {
        String key = normalize(identifier);
        if (key == null) {
            return delegate.getMetadata(identifier);
        }
        IdentifierMetadata cached = metadataCache.get(key);
        if (cached != null) {
            return cached;
        }
        IdentifierMetadata metadata = delegate.getMetadata(key);
        if (metadata != null) {
            metadataCache.put(key, metadata);
            existsCache.put(key, Boolean.TRUE);
        }
        return metadata;
    }
    @Override
    public List<IdentifierMetadata> getMetadataBatch(List<String> identifiers) {
        if (identifiers == null || identifiers.isEmpty()) {
            return delegate.getMetadataBatch(identifiers);
        }
        List<IdentifierMetadata> out = new ArrayList<IdentifierMetadata>(identifiers.size());
        for (String identifier : identifiers) {
            try {
                out.add(getMetadata(identifier));
            } catch (RuntimeException exception) {
                out.add(null);
            }
        }
        return out;
    }
    @Override
    public DigitalObject getDigitalObject(String identifier) {
        String key = normalize(identifier);
        if (key == null) {
            return delegate.getDigitalObject(identifier);
        }
        DigitalObject cached = digitalObjectCache.get(key);
        if (cached != null) {
            return cached;
        }
        DigitalObject object = delegate.getDigitalObject(key);
        if (object != null) {
            digitalObjectCache.put(key, object);
            existsCache.put(key, Boolean.valueOf(object.getStatus() != ObjectStatus.DELETED));
            if (object.getMetadata() != null) {
                metadataCache.put(key, object.getMetadata());
            }
            if (object.getResource() != null) {
                resourceCache.put(key, object.getResource());
            } else {
                resourceCache.invalidate(key);
            }
        }
        return object;
    }
    @Override
    public boolean updateMetadata(String identifier, IdentifierMetadata metadata) {
        String key = normalize(identifier);
        boolean updated = delegate.updateMetadata(identifier, metadata);
        if (updated) {
            invalidateMetadata(key);
        }
        return updated;
    }
    @Override
    public boolean patchMetadata(String identifier, Map<String, String> fields) {
        String key = normalize(identifier);
        boolean updated = delegate.patchMetadata(identifier, fields);
        if (updated) {
            invalidateMetadata(key);
        }
        return updated;
    }
    @Override
    public boolean deleteIdentifier(String identifier) {
        String key = normalize(identifier);
        boolean deleted = delegate.deleteIdentifier(identifier);
        if (deleted) {
            invalidateAll(key);
        }
        return deleted;
    }
    @Override
    public Resource getResource(String identifier) {
        String key = normalize(identifier);
        if (key == null) {
            return delegate.getResource(identifier);
        }
        Resource cached = resourceCache.get(key);
        if (cached != null) {
            return cached;
        }
        Resource resource = delegate.getResource(key);
        if (resource != null) {
            resourceCache.put(key, resource);
            existsCache.put(key, Boolean.TRUE);
        }
        return resource;
    }
    @Override
    public boolean upsertResource(String identifier, Resource resource, boolean forceReplaceType) {
        String key = normalize(identifier);
        boolean updated = delegate.upsertResource(identifier, resource, forceReplaceType);
        if (updated) {
            invalidateResource(key);
        }
        return updated;
    }
    @Override
    public boolean removeResource(String identifier) {
        String key = normalize(identifier);
        boolean removed = delegate.removeResource(identifier);
        if (removed) {
            invalidateResource(key);
        }
        return removed;
    }
    private void invalidateMetadata(String identifier) {
        if (identifier == null) {
            return;
        }
        metadataCache.invalidate(identifier);
        digitalObjectCache.invalidate(identifier);
    }
    private void invalidateResource(String identifier) {
        if (identifier == null) {
            return;
        }
        resourceCache.invalidate(identifier);
        digitalObjectCache.invalidate(identifier);
    }
    private void invalidateAll(String identifier) {
        if (identifier == null) {
            return;
        }
        existsCache.invalidate(identifier);
        metadataCache.invalidate(identifier);
        resourceCache.invalidate(identifier);
        digitalObjectCache.invalidate(identifier);
    }
    private String normalize(String identifier) {
        if (identifier == null) {
            return null;
        }
        String value = identifier.trim();
        return value.isEmpty() ? null : value;
    }
}