package com.course.springlearning.report.service;

import com.course.springlearning.order.entity.OrderStatus;
import com.course.springlearning.report.dto.ReportOrderResponse;
import com.course.springlearning.report.dto.ReportPdfFile;
import com.course.springlearning.report.entity.ReportOrder;
import com.course.springlearning.report.entity.ReportProgress;
import com.course.springlearning.report.exception.ReportOrderNotFoundException;
import com.course.springlearning.report.exception.ReportPdfNotAvailableException;
import com.course.springlearning.report.repository.ReportOrderRepository;
import com.course.springlearning.user.dto.PageResponse;
import com.course.springlearning.user.entity.User;
import com.course.springlearning.user.exception.UserNotFoundException;
import com.course.springlearning.user.repository.UserRepository;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Locale;

@Service
public class ReportOrderService {

    // The two order statuses every report calculates
    private static final List<OrderStatus> REPORTED_STATUSES = List.of(OrderStatus.COMPLETED, OrderStatus.IN_PROGRESS);

    private final ReportOrderRepository reportOrderRepository;
    private final UserRepository userRepository;
    private final ReportOrderProcessor reportOrderProcessor;

    public ReportOrderService(ReportOrderRepository reportOrderRepository, UserRepository userRepository,
                              ReportOrderProcessor reportOrderProcessor) {
        this.reportOrderRepository = reportOrderRepository;
        this.userRepository = userRepository;
        this.reportOrderProcessor = reportOrderProcessor;
    }

    /**
     * Creates one IN_PROGRESS report row per status (COMPLETED and IN_PROGRESS) and starts the AI calculation
     * in the background. Returns immediately, without waiting for the AI.
     */
    @Transactional
    public List<ReportOrderResponse> create(Long userId, String actor) {
        User user = userRepository.findByIdAndDeletedFalse(userId)
                .orElseThrow(() -> new UserNotFoundException(userId));

        Instant now = Instant.now().truncatedTo(ChronoUnit.MICROS);
        List<ReportOrder> reports = REPORTED_STATUSES.stream().map(status -> {
            ReportOrder report = new ReportOrder();
            report.setUser(user);
            report.setOrderStatus(status);
            report.setTotalAmount(null);
            report.setReportProgress(ReportProgress.IN_PROGRESS);
            report.setCreatedAt(now);
            report.setCreatedBy(actor);
            report.setUpdatedAt(now);
            report.setUpdatedBy(actor);
            return report;
        }).toList();

        List<ReportOrder> saved = reportOrderRepository.saveAllAndFlush(reports);
        List<Long> reportIds = saved.stream().map(ReportOrder::getId).toList();

        // Start after commit, so the background thread always finds the rows
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                reportOrderProcessor.process(userId, reportIds);
            }
        });

        return saved.stream().map(ReportOrderResponse::from).toList();
    }

    /** The reports of one user. */
    @Transactional(readOnly = true)
    public PageResponse<ReportOrderResponse> findAll(Long userId, Pageable pageable) {
        return PageResponse.from(reportOrderRepository.findAllByUser_Id(userId, pageable).map(ReportOrderResponse::from));
    }

    /**
     * The PDF of a COMPLETED report of the user, e.g. file name report-order-7-completed.pdf.
     * A report of another user is reported as not found, so report ids of other users are not revealed.
     */
    @Transactional(readOnly = true)
    public ReportPdfFile getPdf(Long id, Long userId) {
        ReportOrder report = reportOrderRepository.findByIdAndUser_Id(id, userId)
                .orElseThrow(() -> new ReportOrderNotFoundException(id));
        if (report.getReportProgress() != ReportProgress.COMPLETED || report.getPdfReport() == null) {
            throw new ReportPdfNotAvailableException(id, report.getReportProgress());
        }
        String fileName = "report-order-" + id + "-" + report.getOrderStatus().name().toLowerCase(Locale.ROOT)
                .replace('_', '-') + ".pdf";
        return new ReportPdfFile(fileName, report.getPdfReport());
    }
}
