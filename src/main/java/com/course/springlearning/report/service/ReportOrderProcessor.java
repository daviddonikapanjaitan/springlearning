package com.course.springlearning.report.service;

import com.course.springlearning.report.ai.OrderReportAiClient;
import com.course.springlearning.report.dto.ReportResult;
import com.course.springlearning.report.entity.ReportOrder;
import com.course.springlearning.report.entity.ReportProgress;
import com.course.springlearning.report.repository.ReportOrderRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * Runs the AI calculation in the background and stores the result:
 * report_progress IN_PROGRESS -> COMPLETED with total_amount, or FAILED when anything goes wrong.
 */
@Component
public class ReportOrderProcessor {

    private static final Logger log = LoggerFactory.getLogger(ReportOrderProcessor.class);

    // Stored in updated_by when the background process finishes a report
    private static final String PROCESSOR_ACTOR = "report-ai";

    private final OrderReportAiClient aiClient;
    private final ReportOrderRepository reportOrderRepository;
    private final TransactionTemplate transactionTemplate;

    public ReportOrderProcessor(OrderReportAiClient aiClient, ReportOrderRepository reportOrderRepository,
                                TransactionTemplate transactionTemplate) {
        this.aiClient = aiClient;
        this.reportOrderRepository = reportOrderRepository;
        this.transactionTemplate = transactionTemplate;
    }

    // Runs on Spring Boot's task executor thread pool, not on the HTTP request thread
    @Async
    public void process(Long userId, List<Long> reportIds) {
        try {
            ReportResult result = aiClient.createReport(userId);
            finish(reportIds, ReportProgress.COMPLETED, result);
            log.info("Report {} for user {} completed: {}", reportIds, userId, result);
        } catch (RuntimeException e) {
            log.error("Report {} for user {} failed", reportIds, userId, e);
            try {
                finish(reportIds, ReportProgress.FAILED, null);
            } catch (RuntimeException saveError) {
                log.error("Could not mark report {} as FAILED", reportIds, saveError);
            }
        }
    }

    // A null result leaves total_amount and report_summary empty (used for FAILED)
    private void finish(List<Long> reportIds, ReportProgress progress, ReportResult result) {
        transactionTemplate.executeWithoutResult(status -> {
            Instant now = Instant.now().truncatedTo(ChronoUnit.MICROS);
            for (ReportOrder report : reportOrderRepository.findAllById(reportIds)) {
                if (result != null) {
                    switch (report.getOrderStatus()) {
                        case COMPLETED -> {
                            report.setTotalAmount(result.completedTotalAmount());
                            report.setReportSummary(result.completedSummary().trim());
                        }
                        case IN_PROGRESS -> {
                            report.setTotalAmount(result.inProgressTotalAmount());
                            report.setReportSummary(result.inProgressSummary().trim());
                        }
                        case PENDING -> throw new IllegalStateException("Report " + report.getId() + " has status PENDING");
                    }
                }
                report.setReportProgress(progress);
                report.setUpdatedAt(now);
                report.setUpdatedBy(PROCESSOR_ACTOR);
            }
        });
    }
}
