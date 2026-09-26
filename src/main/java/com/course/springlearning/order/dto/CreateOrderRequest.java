package com.course.springlearning.order.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

// The limits keep quantity * itemPrice within a BIGINT
public record CreateOrderRequest(
        @NotNull @Positive
        Long userId,

        @NotBlank @Size(max = 255)
        String itemName,

        @NotNull @Positive @Max(1_000_000)
        Long quantity,

        @NotNull @PositiveOrZero @Max(1_000_000_000_000L)
        Long itemPrice,

        @Size(max = 1000)
        String orderDescription
) {
}
