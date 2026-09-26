package com.course.springlearning.report.exception;

public class ReportOrderNotFoundException extends RuntimeException {

    public ReportOrderNotFoundException(Long id) {
        super("Report order with id " + id + " not found");
    }
}
