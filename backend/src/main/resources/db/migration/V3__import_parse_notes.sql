-- Milestone 8: keep extraction notes (mode used, ignored lines/columns) with each import job.
ALTER TABLE import_jobs ADD COLUMN parse_notes VARCHAR(2000);
