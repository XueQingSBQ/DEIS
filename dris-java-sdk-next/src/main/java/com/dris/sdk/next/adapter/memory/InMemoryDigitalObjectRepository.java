package com.dris.sdk.next.adapter.memory;

import com.dris.sdk.next.domain.DigitalObject;
import com.dris.sdk.next.exception.ConflictException;
import com.dris.sdk.next.repository.DigitalObjectRepository;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

public class InMemoryDigitalObjectRepository implements DigitalObjectRepository {
    private final ConcurrentHashMap<String, DigitalObject> store = new ConcurrentHashMap<String, DigitalObject>();

    @Override
    public Optional<DigitalObject> findByIdentifier(String identifier) {
        DigitalObject object = store.get(identifier);
        return Optional.ofNullable(object == null ? null : object.copy());
    }

    @Override
    public boolean existsByIdentifier(String identifier) {
        return store.containsKey(identifier);
    }

    @Override
    public void insert(DigitalObject object) {
        DigitalObject existing = store.putIfAbsent(object.getIdentifier(), object.copy());
        if (existing != null) {
            throw new ConflictException("identifier already exists: " + object.getIdentifier());
        }
    }

    @Override
    public boolean update(final DigitalObject object, final long expectedRevision) {
        final AtomicBoolean updated = new AtomicBoolean(false);
        store.compute(object.getIdentifier(), (key, existing) -> {
            if (existing == null || existing.getRevision() != expectedRevision) {
                return existing;
            }
            updated.set(true);
            return object.copy();
        });
        return updated.get();
    }
}
