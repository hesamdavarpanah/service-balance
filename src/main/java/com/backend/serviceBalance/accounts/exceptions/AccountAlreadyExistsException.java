package com.backend.serviceBalance.accounts.exceptions;

public class AccountAlreadyExistsException extends RuntimeException {

    public AccountAlreadyExistsException(String message, Throwable cause) {
        super(message, cause);
    }
}