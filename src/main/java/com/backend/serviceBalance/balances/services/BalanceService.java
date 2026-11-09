package com.backend.serviceBalance.balances.services;

import reactor.core.publisher.Mono;

public interface BalanceService {

    Mono<Void> credit(String accountId, Long amount, String transactionId);

    Mono<Void> debit(String accountId, Long amount, String transactionId);

    Mono<Void> transfer(String sourceAccountId, String destinationAccountId, Long amount, String transactionId);

    Mono<Long> getBalance(String accountId);

}