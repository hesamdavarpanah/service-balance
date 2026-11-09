package com.backend.serviceBalance.balances.services;

import com.backend.serviceBalance.accounts.models.Account;
import com.backend.serviceBalance.accounts.repositories.AccountRepository;
import com.backend.serviceBalance.balances.exceptions.IdempotencyConflictException;
import com.backend.serviceBalance.balances.exceptions.InsufficientBalanceException;
import com.backend.serviceBalance.balances.exceptions.TransactionAlreadyCompletedException;
import com.backend.serviceBalance.balances.validators.BalanceValidator;
import com.backend.serviceBalance.configs.CreditRetryPolicy;
import com.backend.serviceBalance.transactions.models.Transaction;
import com.backend.serviceBalance.transactions.models.TransferTransaction;
import com.backend.serviceBalance.transactions.models.enums.TransactionMethod;
import com.backend.serviceBalance.transactions.models.enums.TransactionStatus;
import com.backend.serviceBalance.transactions.repositories.TransactionRepository;
import com.backend.serviceBalance.transactions.repositories.TransferTransactionRepository;
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
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class BalanceServiceImpl implements BalanceService {

    private final AccountRepository accountRepository;
    private final TransactionRepository transactionRepository;
    private final R2dbcEntityTemplate entityTemplate;
    private final TransactionalOperator transactionalOperator;
    private final BalanceValidator balanceValidator;
    private final CircuitBreaker creditCircuitBreaker;
    private final TransferTransactionRepository transferTransactionRepository;

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
        return createPendingTransaction(accountId, amount, transactionId, TransactionMethod.CREDIT);
    }

    private Mono<Transaction> createPendingTransaction(String accountId, Long amount, String transactionId, TransactionMethod transactionMethod) {
        LocalDateTime now = LocalDateTime.now();

        Transaction transaction = Transaction.builder().id(transactionId).createdAt(now).updatedAt(now).transactionMethod(transactionMethod).amount(amount).accountId(accountId).transactionStatus(TransactionStatus.PENDING).build();

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
        return Mono.defer(() -> {
            balanceValidator.validateDebit(accountId, amount, transactionId);

            return Mono.defer(() -> debitOnce(accountId, amount, transactionId)).timeout(Duration.ofSeconds(maxDurationTimeout)).retryWhen(Retry.backoff(maxRetry, Duration.ofMillis(200)).maxBackoff(Duration.ofSeconds(2)).jitter(0.5).filter(error -> !(error instanceof IdempotencyConflictException) && !(error instanceof TransactionAlreadyCompletedException) && !(error instanceof InsufficientBalanceException) && CreditRetryPolicy.isRetryable(error)).onRetryExhaustedThrow((_, signal) -> signal.failure())).transformDeferred(CircuitBreakerOperator.of(creditCircuitBreaker));
        });
    }

    private Mono<Void> debitOnce(String accountId, Long amount, String transactionId) {
        return Mono.defer(() -> {
            Mono<Void> work = accountRepository.findByIdForUpdate(accountId).switchIfEmpty(Mono.error(new IllegalArgumentException("Account not found: " + accountId))).flatMap(account -> transactionRepository.findByIdForUpdate(transactionId).switchIfEmpty(Mono.defer(() -> createPendingTransaction(accountId, amount, transactionId, TransactionMethod.DEBIT))).flatMap(transaction -> processDebitTransaction(account, transaction, accountId, amount, transactionId))).then();

            return transactionalOperator.transactional(work);
        });
    }

    private Mono<Void> processDebitTransaction(Account account, Transaction transaction, String accountId, Long amount, String transactionId) {
        boolean sameOperation = java.util.Objects.equals(transaction.getAccountId(), accountId) && java.util.Objects.equals(transaction.getAmount(), amount) && transaction.getTransactionMethod() == TransactionMethod.DEBIT;

        if (!sameOperation) return Mono.error(new IdempotencyConflictException(transactionId));

        TransactionStatus status = transaction.getTransactionStatus();

        if (status == TransactionStatus.SUCCESS)
            return Mono.error(new TransactionAlreadyCompletedException(transactionId));

        if (status == TransactionStatus.PENDING) return completeDebit(account, transaction).then();

        return Mono.error(new IllegalStateException("Transaction cannot be debited in status " + status + ": " + transactionId));
    }

    private Mono<Transaction> completeDebit(Account account, Transaction transaction) {
        return Mono.defer(() -> {
            Long currentBalance = account.getBalance();
            Long amount = transaction.getAmount();

            if (currentBalance == null || currentBalance < 0)
                return Mono.error(new IllegalStateException("Account balance is invalid"));

            if (amount == null || amount <= 0)
                return Mono.error(new IllegalArgumentException("Amount must be greater than zero"));

            if (amount > currentBalance)
                return Mono.error(new InsufficientBalanceException(account.getId(), currentBalance, amount));

            account.setBalance(currentBalance - amount);

            return accountRepository.save(account).flatMap(_ -> {
                transaction.setTransactionStatus(TransactionStatus.SUCCESS);
                transaction.setUpdatedAt(LocalDateTime.now());

                return transactionRepository.save(transaction);
            });
        });
    }

    @Override
    public Mono<Void> transfer(String sourceAccountId, String destinationAccountId, Long amount, String transactionId) {
        return Mono.defer(() -> {
            balanceValidator.validateTransfer(sourceAccountId, destinationAccountId, amount, transactionId);

            return Mono.defer(() -> transferOnce(sourceAccountId, destinationAccountId, amount, transactionId)).timeout(Duration.ofSeconds(maxDurationTimeout)).retryWhen(Retry.backoff(maxRetry, Duration.ofMillis(200)).maxBackoff(Duration.ofSeconds(2)).jitter(0.5).filter(error -> !(error instanceof IdempotencyConflictException) && !(error instanceof TransactionAlreadyCompletedException) && !(error instanceof InsufficientBalanceException) && !(error instanceof IllegalArgumentException) && CreditRetryPolicy.isRetryable(error)).onRetryExhaustedThrow((_, signal) -> signal.failure())).transformDeferred(CircuitBreakerOperator.of(creditCircuitBreaker));
        });
    }

    private Mono<Void> transferOnce(String sourceAccountId, String destinationAccountId, Long amount, String transactionId) {
        return Mono.defer(() -> {
            boolean sourceFirst = sourceAccountId.compareTo(destinationAccountId) < 0;

            String firstId = sourceFirst ? sourceAccountId : destinationAccountId;

            String secondId = sourceFirst ? destinationAccountId : sourceAccountId;

            Mono<Void> work = lockTransferAccount(firstId, sourceAccountId).flatMap(first -> lockTransferAccount(secondId, sourceAccountId).flatMap(second -> {
                Account source = sourceFirst ? first : second;

                Account destination = sourceFirst ? second : first;

                return transferTransactionRepository.findByIdForUpdate(transactionId).switchIfEmpty(Mono.defer(() -> createPendingTransfer(sourceAccountId, destinationAccountId, amount, transactionId))).flatMap(transaction -> processTransferTransaction(source, destination, transaction, sourceAccountId, destinationAccountId, amount, transactionId));
            })).then();

            return transactionalOperator.transactional(work);
        });
    }

    private Mono<Account> lockTransferAccount(String accountId, String sourceAccountId) {
        String role = accountId.equals(sourceAccountId) ? "Source" : "Destination";

        return accountRepository.findByIdForUpdate(accountId).switchIfEmpty(Mono.error(new IllegalArgumentException(role + " account not found: " + accountId)));
    }

    private Mono<TransferTransaction> createPendingTransfer(String sourceAccountId, String destinationAccountId, Long amount, String transactionId) {
        LocalDateTime now = LocalDateTime.now();

        TransferTransaction transaction = TransferTransaction.builder().id(transactionId).sourceAccountId(sourceAccountId).destinationAccountId(destinationAccountId).amount(amount).transactionStatus(TransactionStatus.PENDING).createdAt(now).updatedAt(now).build();

        return entityTemplate.insert(transaction);
    }

    private Mono<Void> processTransferTransaction(Account source, Account destination, TransferTransaction transaction, String sourceAccountId, String destinationAccountId, Long amount, String transactionId) {
        boolean sameOperation = Objects.equals(transaction.getSourceAccountId(), sourceAccountId) && Objects.equals(transaction.getDestinationAccountId(), destinationAccountId) && Objects.equals(transaction.getAmount(), amount);

        if (!sameOperation) return Mono.error(new IdempotencyConflictException(transactionId));

        TransactionStatus status = transaction.getTransactionStatus();

        if (status == TransactionStatus.SUCCESS)
            return Mono.error(new TransactionAlreadyCompletedException(transactionId));

        if (status != TransactionStatus.PENDING)
            return Mono.error(new IllegalStateException("Transaction cannot be transferred in status " + status + ": " + transactionId));

        return completeTransfer(source, destination, transaction);
    }

    private Mono<Void> completeTransfer(Account source, Account destination, TransferTransaction transaction) {
        return Mono.defer(() -> {
            Long sourceBalance = source.getBalance();
            Long destinationBalance = destination.getBalance();
            Long amount = transaction.getAmount();

            if (sourceBalance == null || sourceBalance < 0)
                return Mono.error(new IllegalStateException("Source account balance is invalid"));

            if (destinationBalance == null || destinationBalance < 0)
                return Mono.error(new IllegalStateException("Destination account balance is invalid"));

            if (amount == null || amount <= 0)
                return Mono.error(new IllegalArgumentException("Amount must be greater than zero"));

            if (amount > sourceBalance)
                return Mono.error(new InsufficientBalanceException(source.getId(), sourceBalance, amount));

            final long newDestinationBalance;

            try {
                newDestinationBalance = Math.addExact(destinationBalance, amount);
            } catch (ArithmeticException exception) {
                return Mono.error(new IllegalArgumentException("Destination balance exceeds the supported range", exception));
            }

            source.setBalance(sourceBalance - amount);
            destination.setBalance(newDestinationBalance);

            return accountRepository.save(source).then(accountRepository.save(destination)).then(Mono.defer(() -> transferTransactionRepository.updateTransferTransactionStatus(transaction.getId(), TransactionStatus.SUCCESS))).flatMap(updatedRows -> {
                if (updatedRows != 1)
                    return Mono.error(new IllegalStateException("Expected to update one transfer transaction, " + "but updated " + updatedRows + ": " + transaction.getId()));

                return Mono.empty();
            });
        });
    }

    @Override
    public Mono<Long> getBalance(String accountId) {
        return accountRepository.findBalanceByAccountId(accountId).switchIfEmpty(Mono.error(new IllegalArgumentException("Account not found or balance is null: " + accountId)));
    }
}