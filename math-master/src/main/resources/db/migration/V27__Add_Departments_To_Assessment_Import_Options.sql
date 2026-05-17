-- Thêm danh mục Sở/Kỳ thi vào cấu hình import đề (bổ sung cho bản ghi đã có từ V26)
UPDATE system_config
SET
    config_value = jsonb_set(
        config_value::jsonb,
        '{departments}',
        '["Sở GD&ĐT Hà Nội","Sở GD&ĐT TP. Hồ Chí Minh","Bộ GD&ĐT"]'::jsonb,
        true
    )::text,
    updated_at = NOW()
WHERE
    config_key = 'assessment_import_options'
    AND NOT (config_value::jsonb ? 'departments');
