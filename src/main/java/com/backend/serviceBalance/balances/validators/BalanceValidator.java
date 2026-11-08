package com.backend.serviceBalance.balances.validators;

import org.springframework.stereotype.Component;

@Component
public class BalanceValidator {

    public void validateCredit(String accountId, Long amount, String transactionId) {
        if (accountId == null || accountId.isBlank()) throw new IllegalArgumentException("accountId must not be blank");

        if (transactionId == null || transactionId.isBlank()) throw new IllegalArgumentException("transactionId must not be blank");

        if (amount == null || amount <= 0) throw new IllegalArgumentException("amount must be greater than zero");
    }

}
