package com.course.springlearning.auth.exception;

// Same message for an unknown username and a wrong password, so usernames cannot be guessed (401)
public class InvalidCredentialsException extends RuntimeException {

    public InvalidCredentialsException() {
        super("Invalid username or password");
    }
}
