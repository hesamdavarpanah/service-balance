package com.backend.serviceBalance.accounts.repositories;

import com.backend.serviceBalance.accounts.models.Account;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.query.Param;
import reactor.core.publisher.Mono;

public interface AccountRepository extends ReactiveCrudRepository<Account, String> {


    @Query("""
            SELECT *
            FROM accounts
            WHERE id = :accountId
            FOR UPDATE
            """)
    Mono<Account> findByIdForUpdate(@Param("accountId") String accountId);

}