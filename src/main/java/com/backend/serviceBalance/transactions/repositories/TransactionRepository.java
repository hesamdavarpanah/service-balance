package com.backend.serviceBalance.transactions.repositories;
import com.backend.serviceBalance.transactions.models.enums.TransactionStatus;
import org.springframework.data.r2dbc.repository.Modifying;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.query.Param;
import reactor.core.publisher.Mono;


import com.backend.serviceBalance.transactions.models.Transaction;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;

public interface TransactionRepository extends ReactiveCrudRepository<Transaction, String> {

    @Modifying
    @Query("""
            UPDATE transactions
            SET transaction_status = :status
            WHERE id = :id
            """)
    Mono<Integer> updateTransactionStatus(@Param("id") String id, @Param("status") TransactionStatus status);

}
