package com.backend.serviceBalance.transactions.payloads.requests;

import com.backend.serviceBalance.transactions.models.enums.TransactionMethod;
import com.backend.serviceBalance.transactions.models.enums.TransactionStatus;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;


@Builder
@Getter
@Setter
public class CreateTransactionRequest {

    private TransactionMethod transactionMethod;

    private Long amount;

    private String accountId;

    private TransactionStatus transactionStatus;

}
