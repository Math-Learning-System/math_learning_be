-- V11__Create_System_Config.sql
-- Creates the system_config table for platform-wide configurable settings.
-- Admin can update config values via the AdminSystemConfigController.
-- Public read access is provided via PublicConfigController (no auth required).

CREATE TABLE IF NOT EXISTS system_config (
    id           UUID         NOT NULL,
    config_key   VARCHAR(255) NOT NULL,
    config_value TEXT         NOT NULL,
    description  VARCHAR(500),
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    created_by   UUID,
    updated_at   TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_by   UUID,
    deleted_at   TIMESTAMPTZ,
    deleted_by   UUID,
    CONSTRAINT pk_system_config PRIMARY KEY (id),
    CONSTRAINT uq_system_config_key UNIQUE (config_key)
);

CREATE INDEX IF NOT EXISTS idx_system_config_key ON system_config (config_key);

-- Seed: initial privacy policy content (JSON)
INSERT INTO system_config (id, config_key, config_value, description, created_at, updated_at)
VALUES (
    gen_random_uuid(),
    'privacy_policy',
    $policy${
  "lastUpdated": "tháng 5 năm 2026",
  "introBanner": "MathMaster chỉ yêu cầu tài liệu xác thực duy nhất một lần. Thông tin của bạn được bảo vệ nghiêm ngặt và không bao giờ được chia sẻ với bên thứ ba vì mục đích thương mại.",
  "sections": [
    {
      "title": "1. Giới thiệu",
      "paragraphs": [
        "MathMaster (\"chúng tôi\", \"của chúng tôi\") cam kết bảo vệ quyền riêng tư và dữ liệu cá nhân của bạn. Chính sách Bảo mật này mô tả cách chúng tôi thu thập, sử dụng, lưu trữ và bảo vệ thông tin trong quá trình xác thực tư cách giảng dạy của giáo viên đăng ký sử dụng nền tảng MathMaster.",
        "Bằng cách hoàn tất quy trình xác thực, bạn xác nhận đã đọc và đồng ý với các điều khoản trong Chính sách này."
      ],
      "bulletPoints": []
    },
    {
      "title": "2. Thông tin chúng tôi thu thập",
      "paragraphs": [
        "Trong quá trình xác thực quyền giảng dạy, chúng tôi thu thập các thông tin sau:"
      ],
      "bulletPoints": [
        "Địa chỉ email (trường học hoặc cá nhân)",
        "Họ và tên đầy đủ",
        "Tên trường và địa chỉ trường công tác",
        "Website trường học (không bắt buộc)",
        "Chức vụ tại trường",
        "Hình ảnh hoặc bản scan Thẻ Cán bộ / Viên chức / Giáo viên"
      ]
    },
    {
      "title": "3. Mục đích sử dụng thông tin",
      "paragraphs": [
        "Thông tin thu thập được sử dụng với các mục đích sau:"
      ],
      "bulletPoints": [
        "Xác minh tư cách và quyền giảng dạy của bạn trên nền tảng MathMaster",
        "Kích hoạt tài khoản giáo viên và cấp quyền truy cập vào các công cụ dành riêng cho giáo viên",
        "Liên lạc thông báo kết quả xét duyệt hồ sơ",
        "Hỗ trợ kỹ thuật và giải quyết các vấn đề phát sinh liên quan đến tài khoản",
        "Cải thiện quy trình xác thực và chất lượng dịch vụ"
      ]
    },
    {
      "title": "4. Lưu trữ và bảo mật dữ liệu",
      "paragraphs": [
        "Chúng tôi áp dụng các biện pháp bảo mật kỹ thuật và tổ chức phù hợp để bảo vệ dữ liệu cá nhân của bạn:"
      ],
      "bulletPoints": [
        "Tất cả dữ liệu được truyền qua kết nối mã hóa HTTPS/TLS",
        "Tài liệu xác thực (ảnh thẻ giáo viên) được lưu trữ trên hệ thống bảo mật, chỉ đội ngũ xét duyệt nội bộ mới có quyền truy cập",
        "Hình ảnh thẻ giáo viên sẽ được xóa vĩnh viễn trong vòng 30 ngày sau khi quá trình xét duyệt hoàn tất",
        "Thông tin cá nhân (họ tên, email, trường học) được lưu trữ trong suốt thời gian tài khoản còn hoạt động",
        "Hệ thống được kiểm tra bảo mật định kỳ để phát hiện và xử lý các lỗ hổng"
      ]
    },
    {
      "title": "5. Chia sẻ thông tin với bên thứ ba",
      "paragraphs": [
        "MathMaster không bán, cho thuê hoặc trao đổi thông tin cá nhân của bạn với bất kỳ bên thứ ba nào vì mục đích thương mại.",
        "Chúng tôi chỉ có thể chia sẻ thông tin trong các trường hợp ngoại lệ sau:"
      ],
      "bulletPoints": [
        "Theo yêu cầu bắt buộc của cơ quan nhà nước hoặc cơ quan pháp luật có thẩm quyền",
        "Khi bạn đã đồng ý rõ ràng và cụ thể cho từng trường hợp chia sẻ",
        "Để bảo vệ quyền, tài sản hoặc sự an toàn của MathMaster, người dùng hoặc cộng đồng"
      ]
    },
    {
      "title": "6. Quyền của bạn",
      "paragraphs": [
        "Là người dùng MathMaster, bạn có các quyền sau đối với dữ liệu cá nhân của mình:"
      ],
      "bulletPoints": [
        "Quyền truy cập: Xem lại thông tin cá nhân tại phần Cài đặt tài khoản",
        "Quyền chỉnh sửa: Cập nhật thông tin cá nhân bất kỳ lúc nào",
        "Quyền xóa: Yêu cầu xóa tài khoản và toàn bộ dữ liệu liên quan",
        "Quyền phản đối: Phản đối việc xử lý dữ liệu trong một số trường hợp nhất định",
        "Quyền rút lại đồng ý: Rút lại sự đồng ý của bạn bất kỳ lúc nào bằng cách liên hệ với chúng tôi"
      ]
    },
    {
      "title": "7. Thay đổi chính sách",
      "paragraphs": [
        "Chúng tôi có thể cập nhật Chính sách Bảo mật này theo thời gian để phản ánh các thay đổi trong hoạt động của chúng tôi hoặc yêu cầu pháp lý. Khi có thay đổi quan trọng, chúng tôi sẽ thông báo qua email hoặc thông báo nổi bật trên nền tảng. Ngày cập nhật lần cuối luôn được ghi rõ ở đầu trang chính sách."
      ],
      "bulletPoints": []
    },
    {
      "title": "8. Liên hệ",
      "paragraphs": [
        "Nếu bạn có câu hỏi, thắc mắc hoặc khiếu nại liên quan đến Chính sách Bảo mật này, vui lòng liên hệ với chúng tôi qua:"
      ],
      "bulletPoints": [
        "Email: privacy@mathmaster.vn",
        "Website: mathmaster.vn"
      ],
      "footer": "Chúng tôi sẽ phản hồi trong vòng 5 ngày làm việc kể từ khi nhận được yêu cầu của bạn."
    }
  ],
  "contactEmail": "dangkhoipham80@gmail.com",
  "contactWebsite": "https://mathmaster.vn",
  "responseTime": "5 ngày làm việc"
}$policy$,
    'Nội dung chính sách bảo mật hiển thị khi người dùng đăng ký tài khoản',
    NOW(),
    NOW()
)
ON CONFLICT (config_key) DO NOTHING;
