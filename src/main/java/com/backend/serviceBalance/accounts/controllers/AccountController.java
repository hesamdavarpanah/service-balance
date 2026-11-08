package com.backend.serviceBalance.accounts.controllers;

import com.backend.serviceBalance.accounts.payloads.requests.CreateAccountRequest;
import com.backend.serviceBalance.accounts.services.AccountService;
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

@Tag(name = "Account Management", description = "Account management can create, read, update and delete accounts")
@RestController
@RequestMapping("${base-url}/users")
public class AccountController {

    private final AccountService accountService;

    public AccountController(AccountService accountService) {
        this.accountService = accountService;
    }

    @Operation(summary = "Create account", description = "Creates a new account using the provided information.", method = "POST", requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "Information required to create a user", required = true, content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = CreateAccountRequest.class))), responses = {@ApiResponse(responseCode = "201", description = "Account created successfully", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = GeneralResponse.class), examples = @ExampleObject(name = "AccountCreated", value = """
            {
              "title": "Success",
              "explanation": "User created successfully"
            }
            """)))})
    @PostMapping(value = "/create-account/", produces = MediaType.APPLICATION_JSON_VALUE, consumes = MediaType.APPLICATION_JSON_VALUE)
    public Mono<ResponseEntity<GeneralResponse>> createUser(@Valid @RequestBody CreateAccountRequest request) {
        return accountService.createUser(request).thenReturn(ResponseEntity.status(HttpStatus.CREATED).body(GeneralResponse.builder().title("Success").explanation("User created successfully").build()));
    }
}