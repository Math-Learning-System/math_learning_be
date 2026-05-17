-- Cấu hình năm học & loại đề cho import PDF (admin chỉnh qua system_config)
INSERT INTO system_config (id, config_key, config_value, description, created_at, updated_at)
VALUES (
    gen_random_uuid(),
    'assessment_import_options',
    '{"schoolYears":["2023-2024","2024-2025","2025-2026"],"examTypes":["Đề chính thức","Đề thử","Đề luyện tập","Đề giữa kỳ","Đề cuối kỳ"]}',
    'Năm học và loại đề dùng khi giáo viên import đề từ PDF',
    NOW(),
    NOW()
)
ON CONFLICT (config_key) DO NOTHING;
