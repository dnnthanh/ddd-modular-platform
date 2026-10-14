# Local và VPS

Mặc định Compose bật profile `observability`, `OTEL_ENABLED=true` và chạy
1 replica ứng dụng. Java gửi metrics/traces vào LGTM; PostgreSQL, Redis và
các container được theo dõi qua exporters/observer. Dashboard Operations mặc định
chọn tất cả Docker service, có thể lọc riêng từng service. Observability hiển thị
kết nối PostgreSQL/Redis và trạng thái mọi service. JVM Overview giữ UID upstream,
được mở rộng bằng `infra/lgtm/resources/jvm-overview.json`, có log chung ba service
Java. Bộ lọc Job/Instance ở JVM chỉ lọc metrics; log chung gom mọi replica.

## Logback

Mặc định Logback ghi JSON và đầy đủ exception ra stdout để Alloy đưa vào Loki.
Chỉ thu log ba service Java, chỉ index label `service_name`; hạ tầng chỉ monitor
metrics. Không ghi file ứng dụng mặc định.

Khi không chạy monitoring, đặt `COMPOSE_PROFILES=` và `OTEL_ENABLED=false`,
exporters Java thành `none`; bật Spring profile `file-logs` để lưu file tại
`.logs/gateway`, `.logs/eureka`, `.logs/modular-app`. File fallback xoay theo
ngày/kích thước (20 MB/file, 7 ngày, tối đa 128 MB/instance).
Giới hạn request JSON 1 MB và audit body preview 8 KB không giới hạn log Java.

## Database

Hai tài khoản dịch vụ: `run` chạy ứng dụng và Liquibase, `backup` chỉ đọc để dump.
`run` sở hữu schema nên có DDL cần cho Liquibase, nhưng không có superuser,
CREATE ROLE hoặc CREATE DATABASE. Không tạo user Liquibase/exporter riêng.
Tạo user/quyền thủ công trước lần khởi động app trên DB mới: chạy SQL riêng
`.local/db-users.sql` bằng tài khoản quản trị vào `modulardb`. File chỉ ở local,
không vào Git hoặc Docker build; deploy không còn tự chạy grant. DB hiện tại
đã có user/quyền nên không cần tạo lại khi restart.
PostgreSQL vẫn có tài khoản quản trị bootstrap và các role hệ thống; credential
quản trị không cấp cho ứng dụng/backup. `.env` là cấu hình chính và không commit.

Bảng tổng hợp health/restart dùng snapshot hiện tại theo service, tránh đếm
series container/health cũ còn giữ trong Prometheus. Observer có healthcheck
kiểm tra HTTP metrics và lần scrape Docker thành công.

Hai exporter có healthcheck `/metrics` và yêu cầu `pg_up`/`redis_up` bằng 1.
Redis exporter giữ binary upstream, bổ sung BusyBox tĩnh để probe vì image gốc
không có shell. `unless-stopped` xử lý process thoát; `unhealthy` chỉ đánh dấu,
không tự restart container.

## Backup và Telegram

Backup chạy ngay khi khởi động, rồi đầu mỗi giờ khi Docker đang chạy, giữ 7 ngày
trong `.backups/postgres/`. Dump custom-format phải đọc được và checksum hợp lệ
trước khi ghi marker hoàn tất; chỉ sau đó mới xóa backup quá hạn. `flock` tránh
chạy chồng; lỗi backup không xóa bản cũ. Healthcheck kiểm tra tuổi marker và checksum.

Một process Python nhỏ trong chính container backup gửi kết quả thành công/lỗi
qua Telegram; không cần monitor hay thêm container. Template tiếng Việt nằm tại
`infra/lgtm/resources/backup-notification-template.json`. Token và chat ID ở `.env`.
Outbox và trạng thái retry lưu local, gửi có retry và không ảnh hưởng kết quả
dump. Event chỉ bị xóa sau khi Telegram nhận; có thể gửi lặp một tin nếu process
chết sau khi Telegram nhận nhưng trước khi xóa event.
Backup chứa dữ liệu nhạy cảm, quyền file 0600/thư mục 0700, không vào Git/image.
Bản lưu cùng host không bảo vệ khỏi mất ổ đĩa; muốn bảo vệ trường hợp đó cần
sao chép ra nơi khác. Dump chứa `modulardb`, không chứa role/config toàn cluster.

## Monitoring

`docker compose up -d` bật monitoring từ `.env`. Sau khi đổi `OTEL_ENABLED`
hoặc exporter, recreate các service Java. Grafana dùng datasource mặc định LGTM.
Grafana OSS Viewer không có phân quyền từng service trong cấu hình này; vì vậy
Loki chỉ nhận log Java, không nhận log Redis/PostgreSQL hay hạ tầng khác.

Template cảnh báo Grafana ở `infra/lgtm/resources/notification-templates.yaml`.
Sửa rồi chạy `./scripts/deploy.sh alerts`.
`GRAFANA_ROOT_URL` mặc định localhost; trên VPS cần đặt URL HTTPS truy cập thực tế.
URL không tự mở port, thiết lập TLS hoặc reverse proxy.

## Cookie và blacklist

Blacklist dùng `CacheHelper` với cache Boolean có TTL JWT + 60 giây; Redis có
AOF và volume. Deploy chuyển các key blacklist cũ sang cache mới trước khi
khởi động ứng dụng mới, không làm sống lại token đã logout.

Refresh token 32 byte ngẫu nhiên được trả bằng cookie HttpOnly. DB chỉ lưu hash
SHA-256, giữ hạn tuyệt đối 30 ngày; mỗi refresh xoay token và replay thu hồi family.
Access token trả trong JSON để FE giữ trong bộ nhớ. Refresh/cookie logout cần
Origin/Referer thuộc `AUTH_ALLOWED_ORIGINS`; Gateway và backend dùng cùng allowlist.
HTTP local dùng `AUTH_COOKIE_SECURE=false`; lên VPS HTTPS phải đặt true.
FE khác origin dùng `credentials: include`, đúng origin được cho phép; SameSite
Strict phù hợp cùng site, không tự đổi sang None cho mọi trường hợp.
Không ghi cookie/token vào log và không lưu refresh token gốc vào DB.
