package com.dris.sdk.next.application;

import com.dris.sdk.next.rule.IdentifierRules;
import com.dris.sdk.next.domain.DigitalObject;
import com.dris.sdk.next.enumtype.ObjectStatus;
import com.dris.sdk.next.exception.ConflictException;
import com.dris.sdk.next.exception.NotFoundException;
import com.dris.sdk.next.infrastructure.TimeProvider;
import com.dris.sdk.next.infrastructure.TransactionRunner;
import com.dris.sdk.next.repository.DigitalObjectRepository;

import java.time.Instant;

abstract class AbstractDigitalObjectService {
    protected final DigitalObjectRepository repository;
    protected final TransactionRunner transactionRunner;
    protected final TimeProvider timeProvider;

    protected AbstractDigitalObjectService(DigitalObjectRepository repository,
                                           TransactionRunner transactionRunner,
                                           TimeProvider timeProvider) {
        this.repository = repository;
        this.transactionRunner = transactionRunner;
        this.timeProvider = timeProvider;
    }

    protected DigitalObject loadActiveObject(String identifier) {
        IdentifierRules.validateIdentifier(identifier);
        DigitalObject object = repository.findByIdentifier(identifier).orElseThrow(new MissingIdentifier(identifier));
        if (object.getStatus() == ObjectStatus.DELETED) {
            throw new NotFoundException("identifier is deleted: " + identifier);
        }
        return object;
    }

    protected long expectedRevisionOrCurrent(Long expectedRevision, DigitalObject object) {
        return expectedRevision == null ? object.getRevision() : expectedRevision.longValue();
    }

    protected Instant now() {
        return timeProvider.now();
    }

    protected String nowText() {
        return now().toString();
    }

    protected void saveUpdatedObject(DigitalObject object, long expectedRevision) {
        if (!repository.update(object, expectedRevision)) {
            throw new ConflictException("revision conflict for identifier: " + object.getIdentifier());
        }
    }

    private static class MissingIdentifier implements java.util.function.Supplier<NotFoundException> {
        private final String identifier;

        private MissingIdentifier(String identifier) {
            this.identifier = identifier;
        }

        @Override
        public NotFoundException get() {
            return new NotFoundException("identifier not found: " + identifier);
        }
    }
}
