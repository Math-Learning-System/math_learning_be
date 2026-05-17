ALTER TABLE assessments
    ADD COLUMN IF NOT EXISTS pdf_import_document_json TEXT;

COMMENT ON COLUMN assessments.pdf_import_document_json IS 'Structured OCR blocks: questionSection + answerSection (JSON)';
