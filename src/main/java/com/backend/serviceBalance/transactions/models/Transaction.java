package com.backend.serviceBalance.transactions.models;

import com.backend.serviceBalance.transactions.models.enums.TransactionMethod;
import com.backend.serviceBalance.transactions.models.enums.TransactionStatus;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Table("transactions")
public class Transaction {

    @Id
    private String id;

    @NotNull
    @Column("created_at")
    private LocalDateTime createdAt;

    @NotNull
    @Column("updated_at")
    private LocalDateTime updatedAt;

    @NotNull
    @Column("transaction_method")
    private TransactionMethod transactionMethod;

    @NotNull
    @Positive
    private Long amount;

    @NotNull
    @Column("account_id")
    private String accountId;

    @NotNull
    @Column("transaction_status")
    private TransactionStatus transactionStatus;

}