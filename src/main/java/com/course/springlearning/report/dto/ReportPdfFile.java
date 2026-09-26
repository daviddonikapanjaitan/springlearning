package com.course.springlearning.report.dto;

// A PDF report ready to download
public record ReportPdfFile(String fileName, byte[] content) {
}
