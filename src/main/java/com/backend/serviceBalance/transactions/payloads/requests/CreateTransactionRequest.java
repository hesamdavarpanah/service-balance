package com.backend.serviceBalance.transactions.payloads.requests;

import com.backend.serviceBalance.transactions.models.enums.TransactionMethod;
import lombok.*;


@Builder
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class CreateTransactionRequest {

    private TransactionMethod transactionMethod;

    private Long amount;

    private String accountId;

}
