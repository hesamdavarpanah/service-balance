package com.backend.serviceBalance.transactions.controllers;

import com.backend.serviceBalance.transactions.payloads.requests.CreateTransferTransactionRequest;
import com.backend.serviceBalance.transactions.payloads.responses.TransferTransactionResponse;
import com.backend.serviceBalance.transactions.services.TransferTransactionService;
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

@Tag(name = "Transfer Transaction Management", description = "Create and retrieve transfer transactions")
@RestController
@RequestMapping("${base-url}/transfer-transactions")
public class TransferTransactionController {

    private final TransferTransactionService transferTransactionService;

    public TransferTransactionController(TransferTransactionService transferTransactionService) {
        this.transferTransactionService = transferTransactionService;
    }

    @Operation(summary = "Create transfer transaction", description = """
            Creates a pending transfer transaction between two existing accounts.
            Source account ID, destination account ID, transaction method
            and amount are required.
            Amount must be greater than zero.
            Source and destination accounts must be different.
            This endpoint does not transfer account balances.
            """, method = "POST", requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "Information required to create a transfer transaction", required = true, content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = CreateTransferTransactionRequest.class))), responses = {@ApiResponse(responseCode = "201", description = "Transfer transaction created successfully", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = GeneralResponse.class), examples = @ExampleObject(name = "TransferTransactionCreated", value = """
            {
              "title": "Success",
              "explanation": "Transfer transaction created successfully"
            }
            """))), @ApiResponse(responseCode = "400", description = """
            Invalid request body or transfer transaction parameters.
            Amount must be greater than zero.
            Source and destination accounts must be different.
            """), @ApiResponse(responseCode = "404", description = "Source or destination account not found")})
    @PostMapping(value = "/create-transfer-transaction/", produces = MediaType.APPLICATION_JSON_VALUE, consumes = MediaType.APPLICATION_JSON_VALUE)
    public Mono<ResponseEntity<GeneralResponse>> createTransferTransaction(@Valid @RequestBody CreateTransferTransactionRequest request) {
        return transferTransactionService.createTransferTransaction(request).thenReturn(ResponseEntity.status(HttpStatus.CREATED).body(GeneralResponse.builder().title("Success").explanation("Transfer transaction created successfully").build()));
    }

    @Operation(summary = "Retrieve transfer transactions", description = """
            Retrieves all transfer transactions.
            Returns an empty array when no transfer transactions exist.
            """, method = "GET", responses = {@ApiResponse(responseCode = "200", description = "Transfer transactions retrieved successfully", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, array = @ArraySchema(schema = @Schema(implementation = TransferTransactionResponse.class))))})
    @GetMapping(value = "/retrieve-transfer-transactions/", produces = MediaType.APPLICATION_JSON_VALUE)
    public Flux<TransferTransactionResponse> retrieveTransferTransactions() {
        return transferTransactionService.retrieveTransferTransactions();
    }
}