package com.backend.serviceBalance.balances.payloads.requests;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Builder
public class GetBalanceRequest {

    @Schema(description = "account ID", example = "ACC-01")
    private String accountId;

}
