package com.course.springlearning.report.exception;

import com.course.springlearning.report.entity.ReportProgress;

public class ReportPdfNotAvailableException extends RuntimeException {

    public ReportPdfNotAvailableException(Long id, ReportProgress progress) {
        super(progress == ReportProgress.COMPLETED
                // Reports completed before PDF reports existed have no PDF
                ? "Report order with id " + id + " has no PDF report, create a new report to get one"
                : "PDF of report order with id " + id + " is not available, report progress is " + progress);
    }
}
