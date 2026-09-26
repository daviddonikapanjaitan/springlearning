package com.course.springlearning.report.dto;

// Structured answer the AI must return after calling the tools: one total and one summary per order status
public record ReportResult(
        Long completedTotalAmount,
        String completedSummary,
        Long inProgressTotalAmount,
        String inProgressSummary
) {
}
