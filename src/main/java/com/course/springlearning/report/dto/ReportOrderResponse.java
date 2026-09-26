package com.course.springlearning.report.dto;

import com.course.springlearning.order.entity.OrderStatus;
import com.course.springlearning.report.entity.ReportOrder;
import com.course.springlearning.report.entity.ReportProgress;

import java.time.Instant;

public record ReportOrderResponse(
        Long id,
        Long userId,
        OrderStatus orderStatus,
        Long totalAmount,
        String reportSummary,
        // The PDF itself is downloaded with GET /api/report-orders/{id}/pdf
        boolean pdfAvailable,
        ReportProgress reportProgress,
        Instant createdAt,
        String createdBy,
        Instant updatedAt,
        String updatedBy
) {

    public static ReportOrderResponse from(ReportOrder report) {
        return new ReportOrderResponse(
                report.getId(),
                // Reading the id of a lazy relation does not load the user
                report.getUser().getId(),
                report.getOrderStatus(),
                report.getTotalAmount(),
                report.getReportSummary(),
                report.getPdfReport() != null,
                report.getReportProgress(),
                report.getCreatedAt(),
                report.getCreatedBy(),
                report.getUpdatedAt(),
                report.getUpdatedBy()
        );
    }
}
