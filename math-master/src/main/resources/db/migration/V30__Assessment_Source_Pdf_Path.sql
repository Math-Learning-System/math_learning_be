-- PDF gốc khi import đề (Cách 2) — object key trên MinIO (bucket slide-templates).
ALTER TABLE assessments
    ADD COLUMN IF NOT EXISTS source_pdf_path VARCHAR(500);

ALTER TABLE assessments
    ADD COLUMN IF NOT EXISTS source_pdf_original_name VARCHAR(255);

COMMENT ON COLUMN assessments.source_pdf_path IS 'MinIO object key, e.g. assessments/pdf-imports/{id}/{uuid}.pdf';
COMMENT ON COLUMN assessments.source_pdf_original_name IS 'Tên file PDF do giáo viên upload';
