package com.backend.serviceBalance.balances.exceptions;

public class InsufficientBalanceException extends RuntimeException {

    public InsufficientBalanceException(String accountId, long currentBalance, long requestedAmount) {
        super("Insufficient balance for account " + accountId + ": currentBalance=" + currentBalance + ", requestedAmount=" + requestedAmount);
    }
}