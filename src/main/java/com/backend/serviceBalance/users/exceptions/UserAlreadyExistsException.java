package com.backend.serviceBalance.users.exceptions;

public class UserAlreadyExistsException extends RuntimeException {

    public UserAlreadyExistsException(String message, Throwable cause) {
        super(message, cause);
    }
}