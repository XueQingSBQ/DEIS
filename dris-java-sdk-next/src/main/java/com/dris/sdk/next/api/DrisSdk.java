package com.dris.sdk.next.api;

public interface DrisSdk extends AutoCloseable {
    IdentifierApplicationService identifiers();

    MetadataApplicationService metadata();

    ResourceApplicationService resources();

    @Override
    void close();
}