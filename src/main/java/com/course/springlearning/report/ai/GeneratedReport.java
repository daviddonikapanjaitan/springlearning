package com.course.springlearning.report.ai;

import com.course.springlearning.order.entity.OrderStatus;
import com.course.springlearning.report.dto.ReportResult;

import java.util.Map;

/**
 * Verified output of one AI report run.
 *
 * @param result the AI's structured answer (totals and summaries)
 * @param pdfs   the PDF file generated through tool calling, by order status
 */
public record GeneratedReport(ReportResult result, Map<OrderStatus, byte[]> pdfs) {
}
