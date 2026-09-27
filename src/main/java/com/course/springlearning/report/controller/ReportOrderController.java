package com.course.springlearning.report.controller;

import com.course.springlearning.auth.security.AuthenticatedUser;
import com.course.springlearning.report.dto.ReportOrderResponse;
import com.course.springlearning.report.dto.ReportPdfFile;
import com.course.springlearning.report.service.ReportOrderService;
import com.course.springlearning.user.dto.PageResponse;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

// Every endpoint needs "Authorization: Bearer <token>": the user comes from the token,
// and the token's username is stored in created_by / updated_by
@RestController
@RequestMapping("/api/report-orders")
public class ReportOrderController {

    private static final int MAX_PAGE_SIZE = 100;

    private final ReportOrderService reportOrderService;

    public ReportOrderController(ReportOrderService reportOrderService) {
        this.reportOrderService = reportOrderService;
    }

    // Reports the orders of the logged-in user.
    // 202 Accepted: the rows are IN_PROGRESS, the AI fills them in the background
    @PostMapping
    public ResponseEntity<List<ReportOrderResponse>> create(@AuthenticationPrincipal AuthenticatedUser user) {
        return ResponseEntity.accepted().body(reportOrderService.create(user.userId(), user.username()));
    }

    // The reports of the logged-in user, newest first
    @GetMapping
    public PageResponse<ReportOrderResponse> findAll(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @AuthenticationPrincipal AuthenticatedUser user) {
        int safePage = Math.max(page, 0);
        int safeSize = Math.clamp(size, 1, MAX_PAGE_SIZE);
        return reportOrderService.findAll(user.userId(),
                PageRequest.of(safePage, safeSize, Sort.by(Sort.Direction.DESC, "id")));
    }

    // Downloads the PDF report; 404 when the report belongs to another user,
    // 409 while the report is still IN_PROGRESS or when it FAILED
    @GetMapping("/{id}/pdf")
    public ResponseEntity<byte[]> downloadPdf(@PathVariable Long id, @AuthenticationPrincipal AuthenticatedUser user) {
        ReportPdfFile pdf = reportOrderService.getPdf(id, user.userId());
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .contentLength(pdf.content().length)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(pdf.fileName()).build().toString())
                .body(pdf.content());
    }
}
