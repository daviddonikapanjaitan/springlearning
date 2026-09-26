-- Short AI-written summary of the report row, NULL until the AI process finishes (and when it fails)
ALTER TABLE report_orders ADD COLUMN report_summary TEXT;
