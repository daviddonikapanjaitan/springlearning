package com.course.springlearning.report.controller;

import com.course.springlearning.report.dto.CreateReportOrderRequest;
import com.course.springlearning.report.dto.ReportOrderResponse;
import com.course.springlearning.report.dto.ReportPdfFile;
import com.course.springlearning.report.service.ReportOrderService;
import com.course.springlearning.user.dto.PageResponse;
import jakarta.validation.Valid;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/report-orders")
public class ReportOrderController {

    // Who performs the action, stored in created_by / updated_by
    private static final String ACTOR_HEADER = "X-Actor";
    private static final String DEFAULT_ACTOR = "system";
    private static final int MAX_PAGE_SIZE = 100;

    private final ReportOrderService reportOrderService;

    public ReportOrderController(ReportOrderService reportOrderService) {
        this.reportOrderService = reportOrderService;
    }

    // 202 Accepted: the rows are IN_PROGRESS, the AI fills them in the background
    @PostMapping
    public ResponseEntity<List<ReportOrderResponse>> create(
            @Valid @RequestBody CreateReportOrderRequest request,
            @RequestHeader(name = ACTOR_HEADER, required = false) String actor) {
        return ResponseEntity.accepted().body(reportOrderService.create(request.userId(), resolveActor(actor)));
    }

    // Newest reports first; userId is optional and filters the reports of one user
    @GetMapping
    public PageResponse<ReportOrderResponse> findAll(
            @RequestParam(required = false) Long userId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        int safePage = Math.max(page, 0);
        int safeSize = Math.clamp(size, 1, MAX_PAGE_SIZE);
        return reportOrderService.findAll(userId, PageRequest.of(safePage, safeSize, Sort.by(Sort.Direction.DESC, "id")));
    }

    // Downloads the PDF report; 409 while the report is still IN_PROGRESS or when it FAILED
    @GetMapping("/{id}/pdf")
    public ResponseEntity<byte[]> downloadPdf(@PathVariable Long id) {
        ReportPdfFile pdf = reportOrderService.getPdf(id);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .contentLength(pdf.content().length)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(pdf.fileName()).build().toString())
                .body(pdf.content());
    }

    private static String resolveActor(String actor) {
        return (actor == null || actor.isBlank()) ? DEFAULT_ACTOR : actor.trim();
    }
}
