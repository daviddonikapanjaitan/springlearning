package com.course.springlearning.report.entity;

import com.course.springlearning.order.entity.OrderStatus;
import com.course.springlearning.user.entity.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

// @Getter/@Setter instead of @Data: a generated toString/equals would load the lazy user relation
@Getter
@Setter
@Entity
@Table(name = "report_orders")
public class ReportOrder {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, updatable = false)
    private User user;

    // Instant is always UTC (+0)
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "created_by", nullable = false, updatable = false, columnDefinition = "text")
    private String createdBy;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "updated_by", nullable = false, columnDefinition = "text")
    private String updatedBy;

    // Only COMPLETED or IN_PROGRESS
    @Enumerated(EnumType.STRING)
    @Column(name = "order_status", nullable = false, updatable = false, columnDefinition = "text")
    private OrderStatus orderStatus;

    // Null until the AI process finishes
    @Column(name = "total_amount")
    private Long totalAmount;

    // AI-written summary, null until the AI process finishes
    @Column(name = "report_summary", columnDefinition = "text")
    private String reportSummary;

    // PDF file of this report (bytea), null until the AI process finishes
    @Column(name = "pdf_report")
    private byte[] pdfReport;

    @Enumerated(EnumType.STRING)
    @Column(name = "report_progress", nullable = false, columnDefinition = "text")
    private ReportProgress reportProgress = ReportProgress.IN_PROGRESS;
}
