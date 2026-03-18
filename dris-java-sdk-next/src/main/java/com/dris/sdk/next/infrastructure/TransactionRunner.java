package com.dris.sdk.next.infrastructure;

public interface TransactionRunner {
    <T> T inTransaction(TransactionalWork<T> work);

    interface TransactionalWork<T> {
        T execute();
    }
}
