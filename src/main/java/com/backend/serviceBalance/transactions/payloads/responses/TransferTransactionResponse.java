package com.backend.serviceBalance.transactions.payloads.responses;

import com.backend.serviceBalance.transactions.models.enums.TransactionStatus;

import java.time.LocalDateTime;

public record TransferTransactionResponse(String id, LocalDateTime createdAt, LocalDateTime updatedAt,
                                          Long amount, String sourceAccountId, String destinationAccountId,
                                          TransactionStatus transactionStatus) {
}
