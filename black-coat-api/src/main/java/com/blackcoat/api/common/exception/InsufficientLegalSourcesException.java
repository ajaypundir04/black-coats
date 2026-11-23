package com.blackcoat.api.common.exception;

/**
 * Thrown when hybrid retrieval finds no statute sections matching the similarity threshold.
 * Per requirements: Bypasses LLM call entirely and refunds the reserved quota.
 */
public class InsufficientLegalSourcesException extends RuntimeException {
    public InsufficientLegalSourcesException(String message) {
        super(message);
    }
}
