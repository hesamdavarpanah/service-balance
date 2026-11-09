package com.backend.serviceBalance.transactions.repositories;

import com.backend.serviceBalance.transactions.models.Transaction;
import com.backend.serviceBalance.transactions.models.enums.TransactionStatus;
import org.springframework.data.r2dbc.repository.Modifying;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Mono;

public interface TransactionRepository extends ReactiveCrudRepository<Transaction, String> {

    @Modifying
    @Query("""
            UPDATE transactions
            SET transaction_status = :status,
                updated_at = CURRENT_TIMESTAMP
            WHERE id = :id
            """)
    Mono<Integer> updateTransactionStatus(@Param("id") String id, @Param("status") TransactionStatus status);

    @Query("""
            SELECT *
            FROM transactions
            WHERE id = :transactionId
            FOR UPDATE
            """)
    Mono<Transaction> findByIdForUpdate(@Param("transactionId") String transactionId);
}