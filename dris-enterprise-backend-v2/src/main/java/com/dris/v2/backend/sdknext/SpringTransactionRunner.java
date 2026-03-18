package com.dris.v2.backend.sdknext;

import com.dris.sdk.next.infrastructure.TransactionRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Component
public class SpringTransactionRunner implements TransactionRunner {
    private final TransactionTemplate transactionTemplate;

    public SpringTransactionRunner(PlatformTransactionManager transactionManager) {
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    @Override
    public <T> T inTransaction(final TransactionalWork<T> work) {
        return transactionTemplate.execute(status -> work.execute());
    }
}
