package com.dris.sdk.next.adapter.memory;

import com.dris.sdk.next.infrastructure.TransactionRunner;

public class LocalTransactionRunner implements TransactionRunner {
    @Override
    public <T> T inTransaction(TransactionalWork<T> work) {
        return work.execute();
    }
}
