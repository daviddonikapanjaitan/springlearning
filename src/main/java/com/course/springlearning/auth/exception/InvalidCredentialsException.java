package com.course.springlearning.auth.exception;

// Same message for an unknown email and a wrong password, so registered emails cannot be guessed (401)
public class InvalidCredentialsException extends RuntimeException {

    public InvalidCredentialsException() {
        super("Invalid email or password");
    }
}
