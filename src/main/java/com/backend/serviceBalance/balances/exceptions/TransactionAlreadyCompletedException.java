package com.backend.serviceBalance.balances.exceptions;

public class TransactionAlreadyCompletedException extends RuntimeException {

    public TransactionAlreadyCompletedException(String transactionId) {
        super("Transaction already completed: " + transactionId);
    }
}