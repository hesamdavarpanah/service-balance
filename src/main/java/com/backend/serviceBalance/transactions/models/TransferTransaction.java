package com.backend.serviceBalance.transactions.models;

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
@Table("transfer_transactions")
public class TransferTransaction {

    @Id
    private String id;

    @NotNull
    @Column("created_at")
    private LocalDateTime createdAt;

    @NotNull
    @Column("updated_at")
    private LocalDateTime updatedAt;

    @NotNull
    @Column("source_account_id")
    private String sourceAccountId;

    @NotNull
    @Column("destination_account_id")
    private String destinationAccountId;

    @NotNull
    @Positive
    private Long amount;

    @NotNull
    @Column("transaction_status")
    private TransactionStatus transactionStatus;
}