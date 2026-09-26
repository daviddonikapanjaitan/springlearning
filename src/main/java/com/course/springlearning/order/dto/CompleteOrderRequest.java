package com.course.springlearning.order.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

// Must be the user who created the order
public record CompleteOrderRequest(
        @NotNull @Positive
        Long userId
) {
}
