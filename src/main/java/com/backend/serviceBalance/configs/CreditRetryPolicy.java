package com.backend.serviceBalance.configs;

import org.springframework.dao.RecoverableDataAccessException;
import org.springframework.dao.TransientDataAccessException;

import java.util.concurrent.TimeoutException;

public final class CreditRetryPolicy {

    private CreditRetryPolicy() {
    }

    public static boolean isRetryable(Throwable error) {
        return error instanceof TimeoutException || error instanceof TransientDataAccessException || error instanceof RecoverableDataAccessException;
    }
}
