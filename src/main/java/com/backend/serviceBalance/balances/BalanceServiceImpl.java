package com.backend.serviceBalance.balances;

public class BalanceServiceImpl implements BalanceService {
    @Override
    public void credit(String accountId, Long amount, String transactionId) {

    }

    @Override
    public void debit(String accountId, Long amount, String transactionId) {

    }

    @Override
    public void transfer(String sourceAccountId, String destinationAccountId, Long amount, String transactionId) {

    }

    @Override
    public Long getBalance(String accountId) {
        return 0L;
    }
}
