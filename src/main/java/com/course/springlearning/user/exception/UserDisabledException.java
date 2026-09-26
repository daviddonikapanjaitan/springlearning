package com.course.springlearning.user.exception;

public class UserDisabledException extends RuntimeException {

    public UserDisabledException(Long id) {
        super("User with id " + id + " is disabled");
    }
}
