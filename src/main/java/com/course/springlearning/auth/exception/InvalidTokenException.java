package com.course.springlearning.auth.exception;

// The Authorization header holds a token that is invalid, expired or revoked (401)
public class InvalidTokenException extends RuntimeException {

    public InvalidTokenException(String message) {
        super(message);
    }
}
