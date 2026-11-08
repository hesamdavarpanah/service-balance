package com.backend.serviceBalance.balances;

public interface BalanceService {

    void credit(String accountId, Long amount, String transactionId);

    void debit(String accountId, Long amount, String transactionId);

    void transfer(String sourceAccountId, String destinationAccountId, Long amount, String transactionId);

    Long getBalance(String accountId);

}
