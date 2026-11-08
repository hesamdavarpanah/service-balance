package com.backend.serviceBalance.balances.exceptions;

public class IdempotencyConflictException extends RuntimeException {

    public IdempotencyConflictException(String transactionId) {
        super("transactionId belongs to a different operation: " + transactionId);
    }
}
