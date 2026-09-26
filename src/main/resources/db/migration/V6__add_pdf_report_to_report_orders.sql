-- PDF file of the report row (generated with OpenPDF through AI tool calling),
-- NULL until the AI process finishes (and when it fails)
ALTER TABLE report_orders ADD COLUMN pdf_report BYTEA;
