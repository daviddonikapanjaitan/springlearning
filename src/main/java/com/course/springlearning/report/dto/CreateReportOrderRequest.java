package com.course.springlearning.report.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

// The user whose orders are reported
public record CreateReportOrderRequest(
        @NotNull @Positive
        Long userId
) {
}
