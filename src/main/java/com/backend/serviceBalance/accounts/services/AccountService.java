package com.backend.serviceBalance.accounts.services;

import com.backend.serviceBalance.accounts.exceptions.AccountAlreadyExistsException;
import com.backend.serviceBalance.accounts.models.Account;
import com.backend.serviceBalance.accounts.payloads.requests.CreateOrUpdateAccountRequest;
import com.backend.serviceBalance.accounts.repositories.AccountRepository;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import com.backend.serviceBalance.accounts.payloads.responses.AccountResponse;
import reactor.core.publisher.Flux;

@Service
public class AccountService {

    private final AccountRepository accountRepository;
    private final PasswordEncoder passwordEncoder;

    public AccountService(AccountRepository accountRepository, PasswordEncoder passwordEncoder) {
        this.accountRepository = accountRepository;
        this.passwordEncoder = passwordEncoder;
    }

    public Mono<Void> createAccount(CreateOrUpdateAccountRequest request) {
        return Mono.fromCallable(() -> Account.builder().firstname(request.getFirstname()).lastname(request.getLastname()).username(request.getUsername()).email(request.getEmail()).phoneNumber(request.getPhoneNumber()).passwordHash(passwordEncoder.encode(request.getPassword())).balance(0L).status(String.valueOf(request.getStatus())).Role(String.valueOf(request.getRole())).build()).subscribeOn(Schedulers.boundedElastic()).flatMap(accountRepository::save).onErrorMap(DuplicateKeyException.class, exception -> new AccountAlreadyExistsException("user created earlier", exception)).then();
    }

    public Flux<AccountResponse> retrieveAccounts() {
        return accountRepository.findAll().map(account -> new AccountResponse(account.getId(), account.getFirstname(), account.getLastname(), account.getUsername(), account.getEmail(), account.getPhoneNumber(), account.getBalance(), account.getStatus(), account.getRole()));
    }


    public Mono<Void> updateAccount(String accountId, CreateOrUpdateAccountRequest request) {
        return accountRepository.findById(accountId).switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND, "Account not found"))).flatMap(account -> Mono.fromCallable(() -> {
            if (request.getFirstname() != null) account.setFirstname(request.getFirstname());

            if (request.getLastname() != null) account.setLastname(request.getLastname());

            if (request.getUsername() != null) account.setUsername(request.getUsername());

            if (request.getEmail() != null) account.setEmail(request.getEmail());

            if (request.getPhoneNumber() != null) account.setPhoneNumber(request.getPhoneNumber());

            if (request.getStatus() != null) account.setStatus(String.valueOf(request.getStatus()));

            if (request.getRole() != null) account.setRole(String.valueOf(request.getRole()));

            if (request.getPassword() != null && !request.getPassword().isBlank())
                account.setPasswordHash(passwordEncoder.encode(request.getPassword()));

            return account;
        }).subscribeOn(Schedulers.boundedElastic())).flatMap(accountRepository::save).onErrorMap(DuplicateKeyException.class, exception -> new AccountAlreadyExistsException("Account information conflicts with an existing account", exception)).then();
    }
}