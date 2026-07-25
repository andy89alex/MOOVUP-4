package com.example.ratelimiter.api;

public class UnknownUserException extends RuntimeException {
    public UnknownUserException(String userId) {
        super("Unknown user: " + userId);
    }
}
