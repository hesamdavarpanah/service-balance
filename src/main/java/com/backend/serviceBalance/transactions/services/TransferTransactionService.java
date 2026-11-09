package com.backend.serviceBalance.transactions.services;

import com.backend.serviceBalance.accounts.repositories.AccountRepository;
import com.backend.serviceBalance.transactions.models.TransferTransaction;
import com.backend.serviceBalance.transactions.models.enums.TransactionStatus;
import com.backend.serviceBalance.transactions.payloads.requests.CreateTransferTransactionRequest;
import com.backend.serviceBalance.transactions.payloads.responses.TransferTransactionResponse;
import com.backend.serviceBalance.transactions.repositories.TransferTransactionRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;

@Service
public class TransferTransactionService {

    private final AccountRepository accountRepository;
    private final TransferTransactionRepository transferTransactionRepository;

    public TransferTransactionService(AccountRepository accountRepository, TransferTransactionRepository transferTransactionRepository) {
        this.accountRepository = accountRepository;
        this.transferTransactionRepository = transferTransactionRepository;
    }

    public Mono<TransferTransaction> createTransferTransaction(CreateTransferTransactionRequest request) {
        return Mono.defer(() -> {
            if (request == null || request.getSourceAccountId() == null || request.getDestinationAccountId() == null || request.getTransactionMethod() == null || request.getAmount() == null || request.getAmount() <= 0)
                return Mono.error(new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid transaction parameters; amount must be greater than 0"));

            if (request.getSourceAccountId().equals(request.getDestinationAccountId()))
                return Mono.error(new ResponseStatusException(HttpStatus.BAD_REQUEST, "Source and destination accounts must be different"));

            return accountRepository.findById(request.getSourceAccountId()).switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND, "Source account not found"))).flatMap(sourceAccount -> {
                if (request.getAmount() > sourceAccount.getBalance())
                    return Mono.error(new ResponseStatusException(HttpStatus.BAD_REQUEST, "Insufficient source account balance"));

                return accountRepository.findById(request.getDestinationAccountId()).switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND, "Destination account not found"))).flatMap(destinationAccount -> {
                    LocalDateTime now = LocalDateTime.now();

                    TransferTransaction transaction = TransferTransaction.builder().amount(request.getAmount()).sourceAccountId(sourceAccount.getId()).destinationAccountId(destinationAccount.getId()).transactionStatus(TransactionStatus.PENDING).createdAt(now).updatedAt(now).build();

                    return transferTransactionRepository.save(transaction);
                });
            });
        });
    }

    public Flux<TransferTransactionResponse> retrieveTransferTransactions() {
        return transferTransactionRepository.findAll().map(transaction -> new TransferTransactionResponse(transaction.getId(), transaction.getCreatedAt(), transaction.getUpdatedAt(), transaction.getAmount(), transaction.getSourceAccountId(), transaction.getDestinationAccountId(), transaction.getTransactionStatus()));
    }
}