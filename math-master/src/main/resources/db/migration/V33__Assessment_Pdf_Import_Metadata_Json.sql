ALTER TABLE assessments
    ADD COLUMN IF NOT EXISTS pdf_import_metadata_json TEXT;

COMMENT ON COLUMN assessments.pdf_import_metadata_json IS 'Wizard step-1/2 form fields (JSON) for PDF import detail view';
