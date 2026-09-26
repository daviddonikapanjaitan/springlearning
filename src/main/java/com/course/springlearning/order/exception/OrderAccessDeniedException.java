package com.course.springlearning.order.exception;

public class OrderAccessDeniedException extends RuntimeException {

    public OrderAccessDeniedException(Long orderId, Long userId) {
        super("User with id " + userId + " did not create order with id " + orderId);
    }
}
