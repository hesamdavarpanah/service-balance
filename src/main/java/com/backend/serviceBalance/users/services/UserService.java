package com.backend.serviceBalance.users.services;

import com.backend.serviceBalance.users.exceptions.UserAlreadyExistsException;
import com.backend.serviceBalance.users.models.User;
import com.backend.serviceBalance.users.payloads.requests.CreateUserRequest;
import com.backend.serviceBalance.users.repositories.UserRepository;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

@Service
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public UserService(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    public Mono<Void> createUser(CreateUserRequest request) {
        return Mono.fromCallable(() -> User.builder().firstname(request.getFirstname()).lastname(request.getLastname()).username(request.getUsername()).email(request.getEmail()).phoneNumber(request.getPhoneNumber()).passwordHash(passwordEncoder.encode(request.getPassword())).balance(0L).build()).subscribeOn(Schedulers.boundedElastic()).flatMap(userRepository::save).onErrorMap(DuplicateKeyException.class, exception -> new UserAlreadyExistsException("user created earlier", exception)).then();
    }
}