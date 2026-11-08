package com.backend.serviceBalance.transactions.payloads.responses;

import com.backend.serviceBalance.transactions.models.enums.TransactionMethod;
import com.backend.serviceBalance.transactions.models.enums.TransactionStatus;

import java.time.LocalDateTime;

public record TransactionResponse(String id, LocalDateTime createdAt, LocalDateTime updatedAt,
                                  TransactionMethod transactionMethod, Long amount, String accountId,
                                  TransactionStatus transactionStatus) {
}
