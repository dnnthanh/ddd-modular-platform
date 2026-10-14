# Observability hiện tại

Dùng datasource mặc định trong `grafana/otel-lgtm:0.36.0`: Prometheus, Loki,
Tempo, Pyroscope. Bộ `DDD ...` được comment tại
`infra/lgtm/provisioning/datasources.yaml`, không mount. File migration xóa datasource trùng đã hoàn thành và được gỡ bỏ. Dashboard và 17 alert rules cùng folder `DDD Platform`.

## Logs và quyền

Alloy chỉ thu stdout/stderr từ gateway, modular-app và eureka của project.
Loki chỉ index `service_name`: `api-gateway`, `modular-app`, `eureka-server`.
Nội dung log Java, ERROR và exception vẫn giữ theo cấu hình ứng dụng.
Không thu log Nginx/PostgreSQL/Redis/exporter/observer/Alloy/LGTM vào Loki;
những service này vẫn được theo dõi bằng metrics. Panel log hạ tầng được bỏ.
Loki Java dùng volume riêng; dữ liệu log cũ ở volume LGTM đã được xóa theo yêu cầu.

Grafana OSS Viewer có thể query các datasource trong organization. Dropdown hoặc
folder permission không giới hạn dữ liệu theo `service_name`. Setup này dùng
phương án fallback người dùng chọn: chỉ ingest log Java, không tạo org, tenant,
account hay phân quyền custom. Viewer có thể đọc **cả ba service Java**, không có
phân quyền từng service; metrics/trace vẫn tuân theo quyền datasource mặc định.
Tài liệu: https://grafana.com/docs/grafana/latest/administration/data-source-management/

## Ports

Chỉ API edge 8088, Grafana 3000, gateway 19080, Eureka 8761 và port riêng cho 3
replica app trong `.env` được publish trên 127.0.0.1. Đã đóng host port PostgreSQL,
Redis, exporters, Docker socket proxy, Alloy, OTLP, Prometheus, Loki, Tempo,
Pyroscope. Direct Java ports là debug local, bỏ qua kiểm soát Nginx/Gateway.
Không phải cấu hình public/VPS; Docker API bên trong vẫn cần collector access.

## Cleanup và kiểm chứng

Đã dọn thư mục `target`, Python `__pycache__`, log/probe/snapshot cũ của Codex
trong `.deploy/review`, `.deploy/security-fixes`, `.deploy/feedback-fixes`.
Giữ `.env`, JWT secrets, source/tests/infra, IDE config, libs OpenTelemetry,
`.deploy/state.env` và lịch sử deployment dùng bởi scripts/observer.

Kiểm chứng: Compose config hợp lệ; Alloy validate pass; 68 test Python pass.
Grafana có 4 datasource mặc định, một folder project với 4 dashboard và 17 alert
rules. Query Loki trả đúng ba service Java và chỉ label `service_name`; mọi
container running, các service có healthcheck đều healthy. Không build lại Java
vì thay đổi này chỉ sửa observability/config/dashboard và dọn build output.

## Telegram

Điền `TELEGRAM_BOT_TOKEN` và `TELEGRAM_CHAT_ID` trong `.env`, rồi chạy
`./scripts/deploy.sh up`. Script tạo `alerting/telegram.yaml` từ template chỉ
chứa tham chiếu biến môi trường; file sinh ra và `.env` không được commit.
`.env.example` giữ cùng cấu hình local nhưng chỉ chứa mật khẩu mẫu, không có
token hay Chat ID thật.

Webhook của bot và gửi cảnh báo bằng `sendMessage` có thể dùng cùng nhau.
Không gọi `deleteWebhook` để lấy ID: gửi `/start` trong chat riêng hoặc
`/start@thanhdn28_bot` trong group có bot, rồi đọc `message.chat.id` từ payload
webhook. Với group, cũng có thể mở Telegram Web `/a/` và lấy số sau `#`
trên URL, giữ dấu âm. `getUpdates` không hoạt động khi webhook còn cấu hình.

Rule scrape metrics chọn label `service` do OTLP bridge cung cấp. Counter qua
bridge có hậu tố `_total`; các query PostgreSQL/HTTP/JVM đã dùng tên thực tế.
HTTP 5xx trả zero theo service khi chưa có lỗi, tránh NoData giả.

## Template tiếng Việt và URL

Sửa `infra/lgtm/resources/notification-templates.yaml`: đây là resource nguồn được
`deploy.sh` chép vào thư mục provisioning, file sinh ra không commit. Telegram contact point gọi template
`ddd.telegram.vi`, dùng plain text và thời gian Asia/Ho_Chi_Minh. Tóm tắt và
hướng xử lý của 17 rule được đặt tiếng Việt trong `alerting/rules.yaml`.
Chạy `./scripts/deploy.sh alerts` để sinh lại provisioning và recreate `lgtm`,
không build lại Java.

`GRAFANA_ROOT_URL` trong `.env` mặc định `http://localhost:3000/`. Trên VPS,
đặt thành địa chỉ Grafana truy cập thực tế, ví dụ `https://grafana.example.com/`,
rồi recreate `lgtm`. Link không tự đổi theo host build; URL chỉ tạo link, không
tự mở port hoặc cấu hình reverse proxy/TLS. Email chưa được bật.

## Backup PostgreSQL mỗi giờ

Service `postgres-backup` tạo dump ngay khi khởi động, rồi vào đầu mỗi giờ khi
Docker đang chạy. Nếu dump kéo dài qua giờ hẹn, chạy bù một lần sau khi dump
trước kết thúc; không chạy chồng nhiều dump. File nằm trong `.backups/postgres/`, định dạng custom của
PostgreSQL, kèm checksum SHA-256. `BACKUP_RETENTION_DAYS=7` trong `.env` điều
khiển thời gian lưu. Chỉ publish archive sau khi dump, đọc danh mục và kiểm tra checksum thành
công; dump thất bại không xóa backup cũ. Các lần chạy được khóa bằng flock,
tên file có timestamp và PID để tránh ghi đè khi gọi thủ công. Healthcheck
kiểm tra marker hoàn thành và cặp archive/checksum, báo unhealthy nếu hơn
125 phút chưa có backup thành công. Máy ngủ hoặc Docker dừng thì job không chạy.

`.backups/` bị loại khỏi Git và Docker build. Backup có dữ liệu nhạy cảm, được
tạo với quyền 0600 trong thư mục 0700. Bản lưu hiện ở cùng host: cần sao chép
ra nơi khác nếu muốn phục hồi khi mất host/ổ đĩa. Không tự restore vào DB chính.

Tạo thêm một bản thủ công: `docker compose exec -T postgres-backup sh /backup/backup.sh once`.
Kiểm tra checksum: chạy `sha256sum -c <ten-file>.dump.sha256` tại thư mục backup.
Khôi phục vào database trống bằng `pg_restore --no-owner --no-acl`; tạo database
đích trước, kiểm chứng dữ liệu rồi mới quyết định thay DB chính. Dump này chứa
`modulardb`, không chứa tài khoản/role toàn cluster hoặc PostgreSQL config.
