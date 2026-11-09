package com.backend.serviceBalance.balances.payloads.requests;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TransferRequest {

    @Schema(description = "your account ID", example = "ACC-01")
    private String sourceAccountId;

    @Schema(description = "target account ID", example = "ACC-02")
    private String destinationAccountId;

    @Schema(description = "transfer account balance amount", example = "1000000")
    private Long amount;

    @Schema(description = "transaction ID", example = "TX-01")
    private String transactionId;

}
