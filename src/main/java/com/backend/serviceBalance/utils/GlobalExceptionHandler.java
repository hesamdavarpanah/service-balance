package com.backend.serviceBalance.utils;

import com.backend.serviceBalance.users.exceptions.UserAlreadyExistsException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.bind.support.WebExchangeBindException;
import org.springframework.web.server.ServerWebInputException;

import java.util.stream.Collectors;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(UserAlreadyExistsException.class)
    public ResponseEntity<GeneralResponse> handleUserAlreadyExists(UserAlreadyExistsException exception) {
        return buildResponse(HttpStatus.CONFLICT, "Conflict", exception.getMessage());
    }

    @ExceptionHandler(WebExchangeBindException.class)
    public ResponseEntity<GeneralResponse> handleValidation(WebExchangeBindException exception) {
        String explanation = exception.getBindingResult().getFieldErrors().stream().map(error -> error.getField() + ": " + error.getDefaultMessage()).collect(Collectors.joining(" | "));

        return buildResponse(HttpStatus.BAD_REQUEST, "Validation Error", explanation.isBlank() ? "invalid input data" : explanation);
    }

    @ExceptionHandler(ServerWebInputException.class)
    public ResponseEntity<GeneralResponse> handleInvalidRequest() {
        return buildResponse(HttpStatus.BAD_REQUEST, "Bad Request", "invalid body or method");
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<GeneralResponse> handleUnexpectedException(Exception exception) {
        log.error("Unexpected error while processing request", exception);

        return buildResponse(HttpStatus.INTERNAL_SERVER_ERROR, "Internal Server Error", "error, try again!");
    }

    private ResponseEntity<GeneralResponse> buildResponse(HttpStatus status, String title, String explanation) {
        return ResponseEntity.status(status).body(GeneralResponse.builder().title(title).explanation(explanation).build());
    }
}