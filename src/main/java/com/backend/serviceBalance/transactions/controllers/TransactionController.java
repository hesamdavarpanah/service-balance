package com.backend.serviceBalance.transactions.controllers;

import com.backend.serviceBalance.transactions.payloads.requests.CreateTransactionRequest;
import com.backend.serviceBalance.transactions.payloads.responses.TransactionResponse;
import com.backend.serviceBalance.transactions.services.TransactionService;
import com.backend.serviceBalance.utils.GeneralResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Tag(name = "Transaction Management", description = "Create and retrieve transactions")
@RestController
@RequestMapping("${base-url}/transactions")
public class TransactionController {

    private final TransactionService transactionService;

    public TransactionController(TransactionService transactionService) {
        this.transactionService = transactionService;
    }

    @Operation(summary = "Create transaction", description = """
            Creates a transaction for an existing account.
            Account ID, transaction method, transaction status and amount
            are required. Amount must be greater than zero.
            """, method = "POST", requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "Information required to create a transaction", required = true, content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = CreateTransactionRequest.class))), responses = {@ApiResponse(responseCode = "201", description = "Transaction created successfully", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = GeneralResponse.class), examples = @ExampleObject(name = "TransactionCreated", value = """
            {
              "title": "Success",
              "explanation": "Transaction created successfully"
            }
            """))), @ApiResponse(responseCode = "400", description = """
            Invalid request body or transaction parameters.
            Amount must be greater than zero.
            """), @ApiResponse(responseCode = "404", description = "Account not found")})
    @PostMapping(value = "/create-transaction/", produces = MediaType.APPLICATION_JSON_VALUE, consumes = MediaType.APPLICATION_JSON_VALUE)
    public Mono<ResponseEntity<GeneralResponse>> createTransaction(@Valid @RequestBody CreateTransactionRequest request) {
        return transactionService.createTransaction(request).thenReturn(ResponseEntity.status(HttpStatus.CREATED).body(GeneralResponse.builder().title("Success").explanation("Transaction created successfully").build()));
    }

    @Operation(summary = "Retrieve transactions", description = """
            Retrieves all transactions.
            Returns an empty array when no transactions exist.
            """, method = "GET", responses = {@ApiResponse(responseCode = "200", description = "Transactions retrieved successfully", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, array = @ArraySchema(schema = @Schema(implementation = TransactionResponse.class))))})
    @GetMapping(value = "/retrieve-transactions/", produces = MediaType.APPLICATION_JSON_VALUE)
    public Flux<TransactionResponse> retrieveTransactions() {
        return transactionService.retrieveTransactions();
    }
}