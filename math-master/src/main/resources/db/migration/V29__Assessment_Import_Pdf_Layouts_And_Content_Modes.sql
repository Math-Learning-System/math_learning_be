-- Dạng PDF (chỉ đề / đề+đáp án) và chế độ xử lý nội dung (PDF / LaTeX) — admin chỉnh qua system_config.
UPDATE system_config
SET
    config_value = (
        COALESCE(config_value::jsonb, '{}'::jsonb) || $patch${
  "pdfLayouts": [
    {
      "id": "questions_only",
      "label": "Chỉ đề thi",
      "description": "PDF chỉ có phần đề, không có mục đáp án/lời giải riêng"
    },
    {
      "id": "questions_with_answers",
      "label": "Đề kèm đáp án",
      "description": "PDF gồm đề và phần đáp án hoặc lời giải (thường ở cuối file)"
    }
  ],
  "importContentModes": [
    {
      "id": "pdf",
      "label": "Giữ nội dung PDF",
      "description": "Trích xuất câu hỏi trực tiếp từ PDF (khuyến nghị)",
      "enabled": true
    },
    {
      "id": "latex",
      "label": "Chuyển sang LaTeX",
      "description": "Chuyển công thức và bố cục sang LaTeX — đang phát triển",
      "enabled": false
    }
  ]
}$patch$::jsonb
    )::text,
    description = 'Cấu hình import đề PDF: năm học, loại đề, dạng PDF, chế độ nội dung',
    updated_at = NOW()
WHERE
    config_key = 'assessment_import_options'
    AND NOT (config_value::jsonb ? 'pdfLayouts');
