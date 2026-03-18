package com.dris.sdk.next.repository;

import com.dris.sdk.next.domain.DigitalObject;

import java.util.Optional;

public interface DigitalObjectRepository {
    Optional<DigitalObject> findByIdentifier(String identifier);

    boolean existsByIdentifier(String identifier);

    void insert(DigitalObject object);

    boolean update(DigitalObject object, long expectedRevision);
}
