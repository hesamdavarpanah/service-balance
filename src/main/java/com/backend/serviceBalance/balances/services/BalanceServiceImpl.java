package com.backend.serviceBalance.balances.services;

import com.backend.serviceBalance.accounts.models.Account;
import com.backend.serviceBalance.accounts.repositories.AccountRepository;
import com.backend.serviceBalance.balances.exceptions.IdempotencyConflictException;
import com.backend.serviceBalance.balances.validators.BalanceValidator;
import com.backend.serviceBalance.transactions.models.Transaction;
import com.backend.serviceBalance.transactions.models.enums.TransactionMethod;
import com.backend.serviceBalance.transactions.models.enums.TransactionStatus;
import com.backend.serviceBalance.transactions.repositories.TransactionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.r2dbc.core.R2dbcEntityTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.reactive.TransactionalOperator;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class BalanceServiceImpl implements BalanceService {

    private final AccountRepository accountRepository;
    private final TransactionRepository transactionRepository;
    private final R2dbcEntityTemplate entityTemplate;
    private final TransactionalOperator transactionalOperator;
    private final BalanceValidator balanceValidator;

    @Override
    public Mono<Void> credit(String accountId, Long amount, String transactionId) {
        return Mono.defer(() -> {
            balanceValidator.validateCredit(accountId, amount, transactionId);

            Mono<Void> work = accountRepository.findByIdForUpdate(accountId).switchIfEmpty(Mono.error(new IllegalArgumentException("Account not found: " + accountId))).flatMap(account -> transactionRepository.findById(transactionId).switchIfEmpty(Mono.defer(() -> createPendingTransaction(accountId, amount, transactionId))).flatMap(transaction -> {
                verifySameCredit(transaction, accountId, amount);

                return switch (transaction.getTransactionStatus()) {

                    case SUCCESS -> Mono.just(transaction);

                    case PENDING -> completeCredit(account, transaction);

                    case FAILED -> Mono.error(new IllegalStateException("Transaction has failed: " + transactionId));
                };
            })).then();

            return transactionalOperator.transactional(work);
        });
    }

    private Mono<Transaction> createPendingTransaction(String accountId, Long amount, String transactionId) {
        LocalDateTime now = LocalDateTime.now();

        Transaction transaction = Transaction.builder().id(transactionId).createdAt(now).updatedAt(now).transactionMethod(TransactionMethod.CREDIT).amount(amount).accountId(accountId).transactionStatus(TransactionStatus.PENDING).build();

        return entityTemplate.insert(transaction);
    }

    private Mono<Transaction> completeCredit(Account account, Transaction transaction) {
        return Mono.defer(() -> {
            Long currentBalance = account.getBalance();

            if (currentBalance == null || currentBalance < 0)
                return Mono.error(new IllegalStateException("Account balance is invalid"));

            final long newBalance;

            try {
                newBalance = Math.addExact(currentBalance, transaction.getAmount());
            } catch (ArithmeticException exception) {
                return Mono.error(new IllegalArgumentException("Balance exceeds the supported range", exception));
            }

            account.setBalance(newBalance);

            return accountRepository.save(account).flatMap(_ -> {
                transaction.setTransactionStatus(TransactionStatus.SUCCESS);
                transaction.setUpdatedAt(LocalDateTime.now());

                return transactionRepository.save(transaction);
            });
        });
    }

    private void verifySameCredit(Transaction existing, String accountId, Long amount) {
        boolean sameRequest = existing.getTransactionMethod() == TransactionMethod.CREDIT && Objects.equals(existing.getAccountId(), accountId) && Objects.equals(existing.getAmount(), amount);

        if (!sameRequest) throw new IdempotencyConflictException(existing.getId());
    }

    @Override
    public Mono<Void> debit(String accountId, Long amount, String transactionId) {
        return Mono.error(new UnsupportedOperationException("Debit is not implemented yet"));
    }

    @Override
    public Mono<Void> transfer(String sourceAccountId, String destinationAccountId, Long amount, String transactionId) {
        return Mono.error(new UnsupportedOperationException("Transfer is not implemented yet"));
    }

    @Override
    public Mono<Long> getBalance(String accountId) {
        return accountRepository.findById(accountId).switchIfEmpty(Mono.error(new IllegalArgumentException("Account not found: " + accountId))).map(Account::getBalance);
    }
}