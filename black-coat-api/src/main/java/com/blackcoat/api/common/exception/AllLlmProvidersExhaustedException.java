package com.blackcoat.api.common.exception;

public class AllLlmProvidersExhaustedException extends RuntimeException {
    public AllLlmProvidersExhaustedException(String message) {
        super(message);
    }

    public AllLlmProvidersExhaustedException(String message, Throwable cause) {
        super(message, cause);
    }
}
