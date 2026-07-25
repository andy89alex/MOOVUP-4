package com.example.ratelimiter.exception;

public class UnknownUserException extends RuntimeException {
    public UnknownUserException(String userId) {
        super("Unknown user: " + userId);
    }
}
