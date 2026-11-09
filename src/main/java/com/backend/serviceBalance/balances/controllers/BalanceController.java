package com.backend.serviceBalance.balances.controllers;

import com.backend.serviceBalance.balances.payloads.requests.CreditAndDebitRequest;
import com.backend.serviceBalance.balances.payloads.requests.GetBalanceRequest;
import com.backend.serviceBalance.balances.payloads.requests.TransferRequest;
import com.backend.serviceBalance.balances.services.BalanceService;
import com.backend.serviceBalance.utils.GeneralResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@Tag(name = "Balance Management", description = "Balance management can credit, debit, transfer and retrieve account balances")
@RestController
@RequestMapping("${base-url}/balances")
public class BalanceController {

    private final BalanceService balanceService;

    public BalanceController(BalanceService balanceService) {
        this.balanceService = balanceService;
    }

    @Operation(summary = "credit account", description = "credit an account balance using the provided information.", method = "POST", requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "Information required to charge an account balance", required = true, content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = CreditAndDebitRequest.class))), responses = {@ApiResponse(responseCode = "200", description = "Account charged successfully", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = GeneralResponse.class), examples = @ExampleObject(name = "charged", value = """
            {
              "title": "Success",
              "explanation": "Account balance charged"
            }
            """)))})
    @PostMapping(value = "/credit/", produces = MediaType.APPLICATION_JSON_VALUE, consumes = MediaType.APPLICATION_JSON_VALUE)
    public Mono<ResponseEntity<GeneralResponse>> credit(@Valid @RequestBody CreditAndDebitRequest request) {
        return balanceService.credit(request.getAccountId(), request.getAmount(), request.getTransactionId()).thenReturn(ResponseEntity.status(HttpStatus.OK).body(GeneralResponse.builder().title("Success").explanation("Account balance charged").build()));
    }

    @Operation(summary = "debit account", description = "debit an account balance using the provided information.", method = "POST", requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "Information required to decrease an account balance", required = true, content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = CreditAndDebitRequest.class))), responses = {@ApiResponse(responseCode = "200", description = "Account decreased successfully", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = GeneralResponse.class), examples = @ExampleObject(name = "decreased", value = """
            {
              "title": "Success",
              "explanation": "Account balance decreased"
            }
            """)))})
    @PostMapping(value = "/debit/", produces = MediaType.APPLICATION_JSON_VALUE, consumes = MediaType.APPLICATION_JSON_VALUE)
    public Mono<ResponseEntity<GeneralResponse>> debit(@Valid @RequestBody CreditAndDebitRequest request) {
        return balanceService.debit(request.getAccountId(), request.getAmount(), request.getTransactionId()).thenReturn(ResponseEntity.status(HttpStatus.OK).body(GeneralResponse.builder().title("Success").explanation("Account balance decreased").build()));
    }

    @Operation(summary = "transfer balance account", description = "transfer an account balance using the provided information.", method = "POST", requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "Information required to transfer an account balance", required = true, content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = TransferRequest.class))), responses = {@ApiResponse(responseCode = "200", description = "Amount transferred successfully", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = GeneralResponse.class), examples = @ExampleObject(name = "transferred", value = """
            {
              "title": "Success",
              "explanation": "Amount transferred"
            }
            """)))})
    @PostMapping(value = "/transfer/", produces = MediaType.APPLICATION_JSON_VALUE, consumes = MediaType.APPLICATION_JSON_VALUE)
    public Mono<ResponseEntity<GeneralResponse>> transfer(@Valid @RequestBody TransferRequest request) {
        return balanceService.transfer(request.getSourceAccountId(), request.getDestinationAccountId(), request.getAmount(), request.getTransactionId()).thenReturn(ResponseEntity.status(HttpStatus.OK).body(GeneralResponse.builder().title("Success").explanation("Account balance transferred").build()));
    }

    @Operation(summary = "Account balance", description = "Retrieve an account balance by account ID.", method = "POST", requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "Account ID required to retrieve an account balance", required = true, content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = GetBalanceRequest.class))), responses = {@ApiResponse(responseCode = "200", description = "Account balance 10000", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = GeneralResponse.class), examples = @ExampleObject(name = "Account Balance", value = """
            {
              "title": "Account balance",
              "explanation": "100000"
            }
            """)))})
    @PostMapping(value = "/get-balance/", produces = MediaType.APPLICATION_JSON_VALUE, consumes = MediaType.APPLICATION_JSON_VALUE)
    public Mono<ResponseEntity<GeneralResponse>> getBalance(@Valid @RequestBody GetBalanceRequest request) {
        return balanceService.getBalance(request.getAccountId()).map(balance -> ResponseEntity.ok(GeneralResponse.builder().title("Account balance").explanation(String.valueOf(balance)).build()));
    }

}
