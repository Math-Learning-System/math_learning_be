-- Bổ sung cấp đề thi, loại đơn vị ra đề, danh mục 34 tỉnh/thành (2025).
-- Toàn bộ giá trị do admin chỉnh qua system_config; không hardcode trong application code.
UPDATE system_config
SET
    config_value = (
        COALESCE(config_value::jsonb, '{}'::jsonb) || $patch${
  "adminVersion": "VN_34_PROVINCES_2025",
  "country": "Việt Nam",
  "examScopes": [
    {"id": "national", "label": "Quốc gia", "fields": []},
    {"id": "province_city", "label": "Tỉnh / Thành phố", "fields": ["provinceCity"]},
    {"id": "district", "label": "Quận / Huyện", "fields": ["provinceCity", "district"]},
    {"id": "school", "label": "Trường học", "fields": ["provinceCity", "district", "schoolName"]},
    {"id": "organization", "label": "Tổ chức khác", "fields": []},
    {"id": "internal", "label": "Nội bộ", "fields": []}
  ],
  "organizerTypes": [
    {"id": "department_of_education", "label": "Sở / Phòng GD&ĐT"},
    {"id": "school", "label": "Trường học"},
    {"id": "university", "label": "Đại học / Viện"},
    {"id": "exam_board", "label": "Ban tổ chức thi / Hội đồng thi"},
    {"id": "training_center", "label": "Trung tâm / Doanh nghiệp đào tạo"},
    {"id": "other", "label": "Khác"}
  ],
  "provinceCities": [
    {"name": "Hà Nội", "type": "municipality"},
    {"name": "Huế", "type": "municipality"},
    {"name": "Hải Phòng", "type": "municipality"},
    {"name": "Đà Nẵng", "type": "municipality"},
    {"name": "Thành phố Hồ Chí Minh", "type": "municipality"},
    {"name": "Cần Thơ", "type": "municipality"},
    {"name": "Tuyên Quang", "type": "province"},
    {"name": "Lào Cai", "type": "province"},
    {"name": "Lai Châu", "type": "province"},
    {"name": "Điện Biên", "type": "province"},
    {"name": "Sơn La", "type": "province"},
    {"name": "Cao Bằng", "type": "province"},
    {"name": "Thái Nguyên", "type": "province"},
    {"name": "Lạng Sơn", "type": "province"},
    {"name": "Quảng Ninh", "type": "province"},
    {"name": "Bắc Ninh", "type": "province"},
    {"name": "Phú Thọ", "type": "province"},
    {"name": "Hưng Yên", "type": "province"},
    {"name": "Ninh Bình", "type": "province"},
    {"name": "Thanh Hóa", "type": "province"},
    {"name": "Nghệ An", "type": "province"},
    {"name": "Hà Tĩnh", "type": "province"},
    {"name": "Quảng Trị", "type": "province"},
    {"name": "Quảng Ngãi", "type": "province"},
    {"name": "Gia Lai", "type": "province"},
    {"name": "Khánh Hòa", "type": "province"},
    {"name": "Lâm Đồng", "type": "province"},
    {"name": "Đắk Lắk", "type": "province"},
    {"name": "Đồng Nai", "type": "province"},
    {"name": "Tây Ninh", "type": "province"},
    {"name": "Vĩnh Long", "type": "province"},
    {"name": "Đồng Tháp", "type": "province"},
    {"name": "Cà Mau", "type": "province"},
    {"name": "An Giang", "type": "province"}
  ]
}$patch$::jsonb
    )::text,
    description = 'Cấu hình import đề PDF: năm học, loại đề, cấp đề, đơn vị ra đề, tỉnh/thành',
    updated_at = NOW()
WHERE
    config_key = 'assessment_import_options'
    AND NOT (config_value::jsonb ? 'examScopes');

-- Trường hợp chưa có bản ghi (môi trường bỏ qua V26)
INSERT INTO system_config (id, config_key, config_value, description, created_at, updated_at)
SELECT
    gen_random_uuid(),
    'assessment_import_options',
    $full${
  "adminVersion": "VN_34_PROVINCES_2025",
  "country": "Việt Nam",
  "schoolYears": ["2023-2024", "2024-2025", "2025-2026"],
  "examTypes": ["Đề chính thức", "Đề thử", "Đề luyện tập", "Đề giữa kỳ", "Đề cuối kỳ"],
  "departments": [
    "Sở Giáo dục và Đào tạo Hà Nội",
    "Sở Giáo dục và Đào tạo Thành phố Hồ Chí Minh",
    "Bộ Giáo dục và Đào tạo"
  ],
  "examScopes": [
    {"id": "national", "label": "Quốc gia", "fields": []},
    {"id": "province_city", "label": "Tỉnh / Thành phố", "fields": ["provinceCity"]},
    {"id": "district", "label": "Quận / Huyện", "fields": ["provinceCity", "district"]},
    {"id": "school", "label": "Trường học", "fields": ["provinceCity", "district", "schoolName"]},
    {"id": "organization", "label": "Tổ chức khác", "fields": []},
    {"id": "internal", "label": "Nội bộ", "fields": []}
  ],
  "organizerTypes": [
    {"id": "department_of_education", "label": "Sở / Phòng GD&ĐT"},
    {"id": "school", "label": "Trường học"},
    {"id": "university", "label": "Đại học / Viện"},
    {"id": "exam_board", "label": "Ban tổ chức thi / Hội đồng thi"},
    {"id": "training_center", "label": "Trung tâm / Doanh nghiệp đào tạo"},
    {"id": "other", "label": "Khác"}
  ],
  "provinceCities": [
    {"name": "Hà Nội", "type": "municipality"},
    {"name": "Huế", "type": "municipality"},
    {"name": "Hải Phòng", "type": "municipality"},
    {"name": "Đà Nẵng", "type": "municipality"},
    {"name": "Thành phố Hồ Chí Minh", "type": "municipality"},
    {"name": "Cần Thơ", "type": "municipality"},
    {"name": "Tuyên Quang", "type": "province"},
    {"name": "Lào Cai", "type": "province"},
    {"name": "Lai Châu", "type": "province"},
    {"name": "Điện Biên", "type": "province"},
    {"name": "Sơn La", "type": "province"},
    {"name": "Cao Bằng", "type": "province"},
    {"name": "Thái Nguyên", "type": "province"},
    {"name": "Lạng Sơn", "type": "province"},
    {"name": "Quảng Ninh", "type": "province"},
    {"name": "Bắc Ninh", "type": "province"},
    {"name": "Phú Thọ", "type": "province"},
    {"name": "Hưng Yên", "type": "province"},
    {"name": "Ninh Bình", "type": "province"},
    {"name": "Thanh Hóa", "type": "province"},
    {"name": "Nghệ An", "type": "province"},
    {"name": "Hà Tĩnh", "type": "province"},
    {"name": "Quảng Trị", "type": "province"},
    {"name": "Quảng Ngãi", "type": "province"},
    {"name": "Gia Lai", "type": "province"},
    {"name": "Khánh Hòa", "type": "province"},
    {"name": "Lâm Đồng", "type": "province"},
    {"name": "Đắk Lắk", "type": "province"},
    {"name": "Đồng Nai", "type": "province"},
    {"name": "Tây Ninh", "type": "province"},
    {"name": "Vĩnh Long", "type": "province"},
    {"name": "Đồng Tháp", "type": "province"},
    {"name": "Cà Mau", "type": "province"},
    {"name": "An Giang", "type": "province"}
  ]
}$full$,
    'Cấu hình import đề PDF: năm học, loại đề, cấp đề, đơn vị ra đề, tỉnh/thành',
    NOW(),
    NOW()
WHERE NOT EXISTS (
    SELECT 1 FROM system_config WHERE config_key = 'assessment_import_options'
);
