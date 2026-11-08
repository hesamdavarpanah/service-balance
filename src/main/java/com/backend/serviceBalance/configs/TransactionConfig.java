package com.backend.serviceBalance.configs;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.ReactiveTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.DefaultTransactionDefinition;
import org.springframework.transaction.reactive.TransactionalOperator;

@Configuration
public class TransactionConfig {

    @Bean
    public TransactionalOperator transactionalOperator(ReactiveTransactionManager transactionManager) {
        DefaultTransactionDefinition definition = new DefaultTransactionDefinition();

        definition.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);

        return TransactionalOperator.create(transactionManager, definition);
    }
}
