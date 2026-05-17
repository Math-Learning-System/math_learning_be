ALTER TABLE assessments
    ADD COLUMN IF NOT EXISTS pdf_import_pages_json TEXT;

COMMENT ON COLUMN assessments.pdf_import_pages_json IS 'OCR text per PDF page (JSON array) for manual question authoring';
