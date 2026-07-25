package com.example.ratelimiter.exception;

/**
 * @author Andi Hermanto
 * @since 2026-07-25
 */
public class UnknownUserException extends RuntimeException {
    public UnknownUserException(String userId) {
        super("Unknown user: " + userId);
    }
}
