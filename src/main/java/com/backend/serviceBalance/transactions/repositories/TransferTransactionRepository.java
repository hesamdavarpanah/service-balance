package com.backend.serviceBalance.transactions.repositories;

import com.backend.serviceBalance.transactions.models.TransferTransaction;
import com.backend.serviceBalance.transactions.models.enums.TransactionStatus;
import org.springframework.data.r2dbc.repository.Modifying;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Mono;

public interface TransferTransactionRepository extends ReactiveCrudRepository<TransferTransaction, String> {

    @Modifying
    @Query("""
        UPDATE transfer_transactions
        SET transaction_status = :status,
            updated_at = CURRENT_TIMESTAMP
        WHERE id = :id
        """)
    Mono<Integer> updateTransferTransactionStatus(
            @Param("id") String id,
            @Param("status") TransactionStatus status
    );

    @Query("""
            SELECT *
            FROM transfer_transactions
            WHERE id = :transactionId
            FOR UPDATE
            """)
    Mono<TransferTransaction> findByIdForUpdate(@Param("transactionId") String transactionId);

}
