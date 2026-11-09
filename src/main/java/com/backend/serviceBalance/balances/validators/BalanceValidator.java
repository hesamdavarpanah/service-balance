package com.backend.serviceBalance.balances.validators;

import org.springframework.stereotype.Component;

@Component
public class BalanceValidator {

    public void validateCredit(String accountId, Long amount, String transactionId) {
        if (accountId == null || accountId.isBlank()) throw new IllegalArgumentException("accountId must not be blank");

        if (transactionId == null || transactionId.isBlank()) throw new IllegalArgumentException("transactionId must not be blank");

        if (amount == null || amount <= 0) throw new IllegalArgumentException("amount must be greater than zero");
    }

    public void validateDebit(String accountId, Long amount, String transactionId) {

        if (accountId == null || accountId.isBlank()) throw new IllegalArgumentException("Account ID is required");

        if (transactionId == null || transactionId.isBlank())
            throw new IllegalArgumentException("Transaction ID is required");

        if (amount == null || amount <= 0) throw new IllegalArgumentException("Amount must be greater than zero");
    }

    public void validateTransfer(String sourceAccountId, String destinationAccountId, Long amount, String transactionId) {
        if (sourceAccountId == null || sourceAccountId.isBlank())
            throw new IllegalArgumentException("Source account ID is required");

        if (destinationAccountId == null || destinationAccountId.isBlank())
            throw new IllegalArgumentException("Destination account ID is required");

        if (transactionId == null || transactionId.isBlank())
            throw new IllegalArgumentException("Transaction ID is required");

        if (amount == null || amount <= 0)
            throw new IllegalArgumentException("Amount must be greater than zero");

        if (sourceAccountId.equals(destinationAccountId))
            throw new IllegalArgumentException("Source and destination accounts must be different");
    }

}
