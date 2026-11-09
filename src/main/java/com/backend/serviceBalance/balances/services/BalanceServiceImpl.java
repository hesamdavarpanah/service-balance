package com.backend.serviceBalance.balances.services;

import com.backend.serviceBalance.accounts.models.Account;
import com.backend.serviceBalance.accounts.repositories.AccountRepository;
import com.backend.serviceBalance.balances.exceptions.IdempotencyConflictException;
import com.backend.serviceBalance.balances.exceptions.TransactionAlreadyCompletedException;
import com.backend.serviceBalance.balances.validators.BalanceValidator;
import com.backend.serviceBalance.configs.CreditRetryPolicy;
import com.backend.serviceBalance.transactions.models.Transaction;
import com.backend.serviceBalance.transactions.models.enums.TransactionMethod;
import com.backend.serviceBalance.transactions.models.enums.TransactionStatus;
import com.backend.serviceBalance.transactions.repositories.TransactionRepository;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.reactor.circuitbreaker.operator.CircuitBreakerOperator;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.r2dbc.core.R2dbcEntityTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.reactive.TransactionalOperator;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;

import java.time.Duration;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class BalanceServiceImpl implements BalanceService {

    private final AccountRepository accountRepository;
    private final TransactionRepository transactionRepository;
    private final R2dbcEntityTemplate entityTemplate;
    private final TransactionalOperator transactionalOperator;
    private final BalanceValidator balanceValidator;
    private final CircuitBreaker creditCircuitBreaker;

    @Value("${max-retry}")
    private Long maxRetry;

    @Value("${max-duration-timeout}")
    private Long maxDurationTimeout;

    @Override
    public Mono<Void> credit(String accountId, Long amount, String transactionId) {
        return Mono.defer(() -> {
            balanceValidator.validateCredit(accountId, amount, transactionId);

            return Mono.defer(() -> creditOnce(accountId, amount, transactionId)).timeout(Duration.ofSeconds(maxDurationTimeout)).retryWhen(Retry.backoff(maxRetry, Duration.ofMillis(200)).maxBackoff(Duration.ofSeconds(2)).jitter(0.5).filter(error -> !(error instanceof IdempotencyConflictException) && !(error instanceof TransactionAlreadyCompletedException) && CreditRetryPolicy.isRetryable(error)).onRetryExhaustedThrow((_, signal) -> signal.failure())).transformDeferred(CircuitBreakerOperator.of(creditCircuitBreaker));
        });
    }

    private Mono<Void> creditOnce(String accountId, Long amount, String transactionId) {
        return Mono.defer(() -> {
            Mono<Void> work = accountRepository.findByIdForUpdate(accountId).switchIfEmpty(Mono.error(new IllegalArgumentException("Account not found: " + accountId))).flatMap(account -> transactionRepository.findByIdForUpdate(transactionId).switchIfEmpty(Mono.defer(() -> createPendingTransaction(accountId, amount, transactionId))).flatMap(transaction -> processCreditTransaction(account, transaction, accountId, amount, transactionId))).then();

            return transactionalOperator.transactional(work);
        });
    }

    private Mono<Transaction> createPendingTransaction(String accountId, Long amount, String transactionId) {
        LocalDateTime now = LocalDateTime.now();

        Transaction transaction = Transaction.builder().id(transactionId).createdAt(now).updatedAt(now).transactionMethod(TransactionMethod.CREDIT).amount(amount).accountId(accountId).transactionStatus(TransactionStatus.PENDING).build();

        return entityTemplate.insert(transaction);
    }

    private Mono<Void> processCreditTransaction(Account account, Transaction transaction, String accountId, Long amount, String transactionId) {
        boolean sameOperation = java.util.Objects.equals(transaction.getAccountId(), accountId) && java.util.Objects.equals(transaction.getAmount(), amount) && transaction.getTransactionMethod() == TransactionMethod.CREDIT;

        if (!sameOperation) return Mono.error(new IdempotencyConflictException(transactionId));

        TransactionStatus status = transaction.getTransactionStatus();

        if (status == TransactionStatus.SUCCESS)
            return Mono.error(new TransactionAlreadyCompletedException(transactionId));

        if (status == TransactionStatus.PENDING) return completeCredit(account, transaction).then();

        return Mono.error(new IllegalStateException("Transaction cannot be credited in status " + status + ": " + transactionId));
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
        return accountRepository.findBalanceByAccountId(accountId).switchIfEmpty(Mono.error(new IllegalArgumentException("Account not found or balance is null: " + accountId)));
    }
}