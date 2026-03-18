package com.dris.sdk.next.application;

import com.dris.sdk.next.api.ResourceApplicationService;
import com.dris.sdk.next.application.support.BatchResultBuilder;
import com.dris.sdk.next.application.support.ResourceValidator;
import com.dris.sdk.next.domain.DigitalObject;
import com.dris.sdk.next.domain.Resource;
import com.dris.sdk.next.enumtype.ComponentType;
import com.dris.sdk.next.exception.NotFoundException;
import com.dris.sdk.next.exception.ValidationException;
import com.dris.sdk.next.infrastructure.TimeProvider;
import com.dris.sdk.next.infrastructure.TransactionRunner;
import com.dris.sdk.next.repository.DigitalObjectRepository;
import com.dris.sdk.next.request.BindResourceRequest;
import com.dris.sdk.next.request.RemoveResourceRequest;
import com.dris.sdk.next.result.BatchResult;

import java.util.List;

public class DefaultResourceApplicationService extends AbstractDigitalObjectService implements ResourceApplicationService {
    private final ResourceValidator resourceValidator;

    public DefaultResourceApplicationService(DigitalObjectRepository repository,
                                             TransactionRunner transactionRunner,
                                             TimeProvider timeProvider) {
        this(repository, transactionRunner, timeProvider, new ResourceValidator());
    }

    public DefaultResourceApplicationService(DigitalObjectRepository repository,
                                             TransactionRunner transactionRunner,
                                             TimeProvider timeProvider,
                                             ResourceValidator resourceValidator) {
        super(repository, transactionRunner, timeProvider);
        this.resourceValidator = resourceValidator;
    }

    @Override
    public Resource get(String identifier) {
        DigitalObject object = loadActiveObject(identifier);
        if (object.getResource() == null) {
            throw new NotFoundException("resource not found for identifier: " + identifier);
        }
        return object.getResource().copy();
    }

    @Override
    public BatchResult<Resource> getBatch(List<String> identifiers) {
        BatchResultBuilder<Resource> builder = new BatchResultBuilder<Resource>();
        if (identifiers == null) {
            return builder.build();
        }
        for (String identifier : identifiers) {
            try {
                builder.addSuccess(identifier, get(identifier));
            } catch (RuntimeException exception) {
                builder.addFailure(identifier, exception);
            }
        }
        return builder.build();
    }

    @Override
    public Resource bind(final BindResourceRequest request) {
        return transactionRunner.inTransaction(new TransactionRunner.TransactionalWork<Resource>() {
            @Override
            public Resource execute() {
                return doBind(request);
            }
        });
    }

    @Override
    public void remove(final RemoveResourceRequest request) {
        transactionRunner.inTransaction(new TransactionRunner.TransactionalWork<Void>() {
            @Override
            public Void execute() {
                doRemove(request);
                return null;
            }
        });
    }

    private Resource doBind(BindResourceRequest request) {
        if (request == null) {
            throw new ValidationException("bind resource request is required");
        }
        resourceValidator.validate(request.getResource());
        DigitalObject object = loadActiveObject(request.getIdentifier());
        long expectedRevision = expectedRevisionOrCurrent(request.getExpectedRevision(), object);
        Resource resource = request.getResource().copy();
        resource.setComponentType(ComponentType.fromValue(resource.getComponentType()).wireValue());
        if (resource.getCreatedAt() == null || resource.getCreatedAt().trim().isEmpty()) {
            resource.setCreatedAt(nowText());
        }
        object.setResource(resource);
        object.setRevision(object.getRevision() + 1L);
        saveUpdatedObject(object, expectedRevision);
        return resource.copy();
    }

    private void doRemove(RemoveResourceRequest request) {
        if (request == null) {
            throw new ValidationException("remove resource request is required");
        }
        DigitalObject object = loadActiveObject(request.getIdentifier());
        if (object.getResource() == null) {
            return;
        }
        long expectedRevision = expectedRevisionOrCurrent(request.getExpectedRevision(), object);
        object.setResource(null);
        object.setRevision(object.getRevision() + 1L);
        saveUpdatedObject(object, expectedRevision);
    }
}