package com.backend.serviceBalance.transactions.services;

import com.backend.serviceBalance.transactions.models.enums.TransactionStatus;
import com.backend.serviceBalance.accounts.repositories.AccountRepository;
import com.backend.serviceBalance.transactions.models.Transaction;
import com.backend.serviceBalance.transactions.payloads.requests.CreateTransactionRequest;
import com.backend.serviceBalance.transactions.payloads.responses.TransactionResponse;
import com.backend.serviceBalance.transactions.repositories.TransactionRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;

@Service
public class TransactionService {

    private final AccountRepository accountRepository;
    private final TransactionRepository transactionRepository;

    public TransactionService(AccountRepository accountRepository, TransactionRepository transactionRepository) {
        this.accountRepository = accountRepository;
        this.transactionRepository = transactionRepository;
    }

    public Mono<Transaction> createTransaction(CreateTransactionRequest request) {
        return Mono.defer(() -> {
            if (request == null || request.getAccountId() == null || request.getTransactionMethod() == null || request.getAmount() == null || request.getAmount() <= 0) {

                return Mono.error(new ResponseStatusException(HttpStatus.BAD_REQUEST, "invalid transaction parameters, amount must be greater than 0"));
            }

            return accountRepository.findById(request.getAccountId()).switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND, "account not found"))).flatMap(account -> {
                LocalDateTime now = LocalDateTime.now();

                Transaction transaction = Transaction.builder().transactionMethod(request.getTransactionMethod()).amount(request.getAmount()).accountId(account.getId()).transactionStatus(TransactionStatus.PENDING).createdAt(now).updatedAt(now).build();

                return transactionRepository.save(transaction);
            });
        });
    }

    public Flux<TransactionResponse> retrieveTransactions() {
        return transactionRepository.findAll().map(transaction -> new TransactionResponse(transaction.getId(), transaction.getCreatedAt(), transaction.getUpdatedAt(), transaction.getTransactionMethod(), transaction.getAmount(), transaction.getAccountId(), transaction.getTransactionStatus()));
    }

    public Mono<Void> updateTransactionStatus(String transactionId, TransactionStatus status) {
        return Mono.defer(() -> {
            if (transactionId == null || status == null)
                return Mono.error(new ResponseStatusException(HttpStatus.BAD_REQUEST, "invalid transaction id or status"));

            return transactionRepository.updateTransactionStatus(transactionId, status).flatMap(updatedRows -> {
                if (updatedRows == 0)
                    return Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND, "transaction not found"));

                return Mono.empty();
            });
        });
    }
}