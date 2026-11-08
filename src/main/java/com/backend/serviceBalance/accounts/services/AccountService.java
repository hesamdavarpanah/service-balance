package com.backend.serviceBalance.accounts.services;

import com.backend.serviceBalance.accounts.exceptions.AccountAlreadyExistsException;
import com.backend.serviceBalance.accounts.models.Account;
import com.backend.serviceBalance.accounts.payloads.requests.CreateAccountRequest;
import com.backend.serviceBalance.accounts.repositories.AccountRepository;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

@Service
public class AccountService {

    private final AccountRepository accountRepository;
    private final PasswordEncoder passwordEncoder;

    public AccountService(AccountRepository accountRepository, PasswordEncoder passwordEncoder) {
        this.accountRepository = accountRepository;
        this.passwordEncoder = passwordEncoder;
    }

    public Mono<Void> createUser(CreateAccountRequest request) {
        return Mono.fromCallable(() -> Account.builder().firstname(request.getFirstname()).lastname(request.getLastname()).username(request.getUsername()).email(request.getEmail()).phoneNumber(request.getPhoneNumber()).passwordHash(passwordEncoder.encode(request.getPassword())).balance(0L).build()).subscribeOn(Schedulers.boundedElastic()).flatMap(accountRepository::save).onErrorMap(DuplicateKeyException.class, exception -> new AccountAlreadyExistsException("user created earlier", exception)).then();
    }
}