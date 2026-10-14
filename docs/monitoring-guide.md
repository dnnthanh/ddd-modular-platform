# Hướng dẫn đọc monitoring và xử lý sự cố

Tài liệu này mô tả cấu hình của project: Docker Compose, 3 replica `modular-app`,
Grafana/Loki/Tempo/Prometheus, Java OTel agent, Alloy và ops-observer.
Đây là container Compose, **không phải pod Kubernetes**.

## 1. Đọc nhanh khi nghi service bị lỗi

1. Mở **Operations**, chọn service và xem `Đang chạy / Mong muốn`.
2. Xem `Trạng thái health`: process chạy chưa có nghĩa API/DB đã sẵn sàng.
3. Xem `Auto restarts` và `Khởi động gần nhất`.
4. Mở **Runtime** để xem heap, CPU, Hikari, HTTP 5xx và trạng thái scrape.
5. Mở **Observability** hoặc Loki Explore để đọc exception đúng thời điểm.
6. Nếu metrics/log trống, kiểm tra observer, Alloy và LGTM trước khi kết luận app chết.

Chọn `Last 15 minutes` để điều tra hiện tại, `Last 1 hour` hoặc rộng hơn để xem
lịch sử. Dữ liệu thường trễ một vài chu kỳ polling/scrape (observer mặc định 15 giây,
cộng thời gian đọc Docker và Actuator). Khoảng trống khi restart là bình thường.
Nút Refresh lấy dữ liệu mới; sau khi thay file dashboard cần **F5 tải lại trang**.

### Trạng thái cần phân biệt

| Tín hiệu | Nghĩa | Việc kiểm tra tiếp |
|---|---|---|
| Running = Desired, Healthy | Đủ process và mọi replica chạy đều qua healthcheck | Vẫn kiểm tra request thực tế nếu người dùng báo lỗi |
| Running < Desired | Thiếu replica so với cấu hình | `docker compose ps -a`, startup logs, exit code, Eureka |
| Healthy nhưng Running < Desired | Các replica còn chạy tốt, nhưng hệ thống vẫn thiếu replica | Không chỉ nhìn màu xanh; so sánh hai cột replica |
| Chưa có healthcheck | Process chạy, Compose chưa có phép thử readiness cho service | Không hiểu số Healthy=0 thành service chết |
| Chờ / lỗi healthcheck | Có replica đang starting/unhealthy hoặc trạng thái hỗn hợp | Xem healthcheck output và logs |
| Đã dừng | Không có replica chạy trong snapshot | Phân biệt stop chủ động và process tự thoát |
| Scrape OK | Observer đọc được endpoint metrics | Không đồng nghĩa tất cả nghiệp vụ hoạt động tốt |
| Lỗi scrape | Endpoint metrics không truy cập/đọc được | App startup, mạng, Actuator, observer |
| No data | Không có series/event phù hợp trong khoảng thời gian/bộ lọc | Kiểm tra time range, selector và pipeline; không mặc định đổi thành 0 |

Bảng service dùng snapshot hiện tại theo service. Biểu đồ theo thời gian giữ lịch
sử container cũ; nhiều instance ID trong legend có thể là các lần recreate.

## 2. Các màn hình dùng để làm gì?

| Dashboard | Mục đích chính |
|---|---|
| Operations — Replicas, Builds, Images & Logs | Trạng thái container, replica, image đang chạy, startup và auto restart |
| Observability — Services & Logs | Sức khỏe pipeline monitoring và log Java/API audit |
| Runtime — JVM, GC, Hikari & HTTP | Tài nguyên JVM, pool kết nối và HTTP của các replica |
| Infrastructure — PostgreSQL, Hikari & Redis | DB/Redis và pool PostgreSQL bên trong app |
| JVM Overview (OpenTelemetry) | Metrics JVM/HTTP từ Java agent và log Java chung |
| RED Metrics (classic histogram) | Lưu lượng, lỗi, latency từ histogram dạng bucket |
| RED Metrics (native histogram) | RED từ native histogram; có thể trống nếu không có kiểu metric này |

### Operations: từng vùng và biểu đồ

| Vùng | Cách đọc |
|---|---|
| Selected services — replicas, health & startup | Một dòng/service. Bộ lọc Docker service áp dụng vào bảng. App mặc định hiện tại mong muốn 3, service hạ tầng thường 1 |
| Đang chạy / Mong muốn | So sánh actual và desired; app `2 / 3` là thiếu một replica dù hai replica còn lại healthy |
| Healthcheck OK | Số replica đang chạy có healthcheck healthy; không phải số request thành công |
| Auto restarts | Tổng Docker RestartCount của các container hiện tại trong service; không phải lịch sử restart vĩnh viễn |
| Khởi động gần nhất | Lần start mới nhất trong các replica service; muốn xem riêng từng replica dùng Docker inspect |
| Image drift | 0: image ID đang chạy khớp image mà tag hiện tại trỏ tới; >0: có container lệch. Không kết luận code đã deploy chỉ từ tên tag `local` |
| Observer Docker API status | 1: observer đọc Docker thành công; 0: snapshot có thể thiếu/stale, phải kiểm tra observer/proxy |
| Running gateway instances | Số gateway đang chạy; cấu hình hiện tại là 1 |
| Desired / running / healthy | Lịch sử số replica; nhìn thời điểm scale, startup chậm hoặc replica rơi xuống |
| Docker auto restarts per selected container | RestartCount theo container. Container mới tạo lại bắt đầu bộ đếm mới; dùng cả container ID và thời điểm start |
| Running image evidence | Image SHA, revision, tag và container ID. Chỉ hiện metadata được chọn, không xuất environment chứa password |
| Container health, current state and restart counts | Metadata từng container; health/state giúp tìm replica cụ thể. Cột số là Docker RestartCount |
| Container created at / new IDs | Thời điểm tạo container, khác thời điểm start. Recreate đổi ID; restart cùng container thường giữ ID |
| Live application logs | Log Java trong thời gian đã chọn. Khi có trace ID, sao chép vào Tempo Explore để tìm trace liên quan |

Image drift và replica count là trạng thái vận hành, không chứng minh API nghiệp vụ
đúng. Sau deploy vẫn nên thực hiện một request phù hợp.

### Observability: từng panel

| Panel | Cách đọc |
|---|---|
| LGTM container running | LGTM có process chạy; kết hợp health Docker và Grafana API để kiểm tra readiness |
| Docker API observer health | 1 là lần đọc Docker thành công, 0 là lỗi thu thập |
| App instances healthy | Hiện tại kỳ vọng 3 khi cả ba app qua healthcheck |
| PostgreSQL / Redis connection | `pg_up` / `redis_up`: 1 kết nối được backend, 0 không kết nối được |
| Service overview | Cùng cách đọc bảng Operations, nhưng không có bộ lọc service |
| Gateway structured JSON logs | Log gateway: route, security, rate limit và lỗi forwarding |
| Modular app structured logs | Log nghiệp vụ và exception trong app; nhiều replica cùng `service_name` |
| Eureka structured JSON logs | Đăng ký/discovery và trạng thái registry |
| Common Java logs | Gộp gateway, app và Eureka; không thu log Redis/PostgreSQL/hạ tầng vào Loki |
| HTTP audit — business requests | Method, path, query đã sanitize, status, duration, trace ID và body preview cho route được cho phép |

**HTTP audit không phải log heartbeat.** Không có request phù hợp thì panel có thể
No data dù app healthy. `/actuator/**`, `/error`, login/logout/auth được loại khỏi
HTTP audit. Body preview chỉ bật cho Catalog, tối đa theo `HTTP_AUDIT_MAX_BODY_BYTES`
(mặc định 8192 byte), không phải toàn bộ payload. Token/password không được ghi nguyên văn.

Để kiểm tra audit, đăng nhập bằng client rồi gọi `GET /api/private/catalog` với
Bearer access token. Kiểm tra response, sau đó tìm trong Loki:

```logql
{service_name="modular-app"} | json | event="http.audit"
```

Nếu panel trống: tăng time range, kiểm tra đúng `service_name`, tìm chuỗi
`http.audit` trong log Docker, rồi kiểm tra Alloy/Loki. Nếu Docker có audit nhưng
Loki không có thì điều tra pipeline vận chuyển; không thêm traffic giả định kỳ
chỉ để lấp biểu đồ.

### Runtime: từng biểu đồ

| Biểu đồ | Cách đọc |
|---|---|
| JVM heap used | Bộ nhớ heap Java đang dùng theo service/container, MB; khác RAM toàn container |
| Heap pressure | Heap used/max, %. Cao kéo dài và GC dày có thể thiếu heap; một đỉnh ngắn chưa đủ kết luận |
| GC pauses / sec | Số lần GC pause/giây trong cửa sổ 5 phút |
| GC pause time / sec | Tổng thời gian pause/giây; tăng cùng latency thường cần điều tra allocation/heap |
| Hikari connections — active / idle / max | Ba loại series riêng biệt theo replica/pool, không cộng active với max |
| Hikari waiting threads | Thread đang chờ connection. Cao kéo dài thường do pool hết, query chậm hoặc giữ transaction lâu |
| HTTP request rate | Request/giây; giảm về 0 có thể chỉ là không có traffic |
| HTTP 5xx rate | Lỗi server/giây; đối chiếu audit/exception/trace, không nhầm với lỗi 401/403 |
| Container CPU | Một core đầy tải = 100%; có thể vượt 100% khi dùng nhiều core |
| Container memory usage | RAM Docker đo, gồm JVM heap, native, thread stacks và phần bộ nhớ khác; không chỉ heap |
| JVM scrape — current status per container | Một dòng/container, sample mới nhất giữa các instance ID. OK=đọc được metrics; Lỗi scrape=không đọc được |
| JVM loaded classes | Số class đang loaded; tăng mãi có thể cần điều tra classloader nhưng startup tăng là bình thường |

Nhiều counter được đổi tên sang hậu tố `_total` khi đi qua bridge OTLP/Prometheus.
Các biểu đồ `rate(...[5m])` cần đủ sample; app vừa start có thể trống một thời gian.

### Infrastructure: PostgreSQL, Hikari và Redis

Hikari chạy **trong app**, không chạy trong PostgreSQL exporter. Mỗi replica có
pool riêng. Hiện `HIKARI_MAX_POOL_SIZE=8`, `HIKARI_MIN_IDLE=2`; ba replica tương ứng
tối đa 24 connection từ các pool app và tối thiểu khoảng 6 khi đã khởi tạo.
PostgreSQL còn connection từ exporter, backup và client quản trị, nên tổng DB
connection không nhất thiết bằng tổng active Hikari.

| Panel | Cách đọc / dấu hiệu cần điều tra |
|---|---|
| PostgreSQL exporter health | 1: exporter truy cập DB được; không có nghĩa query nghiệp vụ đều nhanh |
| Redis exporter health | 1: exporter kết nối Redis được |
| Exporter scrape success | Observer đọc exporter thành công. Exporter scrape OK nhưng `pg_up=0` vẫn có nghĩa DB connection bị lỗi |
| Hikari active / idle / max | Active: đang được mượn; idle: sẵn để dùng; max: giới hạn cấu hình. Active gần max liên tục cần xem pending/latency |
| Hikari waiting threads | 0 là không có thread chờ tại lần lấy sample; >0 kéo dài là dấu hiệu pressure |
| Hikari connection timeouts (5m) | Số timeout tăng trong 5 phút; thường kỳ vọng 0 |
| Hikari mean acquisition time | Thời gian trung bình lấy connection, không phải thời gian chạy SQL; tăng kèm pending cần điều tra pool/query |
| PostgreSQL connections | Connection toàn database `modulardb`; đối chiếu `max_connections`, không chỉ pool app |
| PostgreSQL transactions/sec | Tốc độ commit; monitoring và các client khác cũng có thể tạo transaction |
| PostgreSQL deadlock increase | Số deadlock tăng trong 10 phút; >0 xem transaction/lock và retry |
| PostgreSQL buffer hit ratio | Tỷ lệ đọc từ shared buffer. Thấp kéo dài có thể cần xem working set, query và IO; không tự tăng RAM chỉ từ một điểm |
| Redis memory used MiB | RAM Redis dùng; biểu đồ đã đổi byte sang MiB |
| Redis memory utilization | Used/maxmemory. Gần 100% cần xử lý sớm; policy hiện tại `noeviction` nên Redis có thể từ chối ghi thay vì evict |
| Redis clients connected | Số connection client, không phải số user đang đăng nhập |
| Redis evicted keys/sec | Key bị đẩy ra vì memory policy. Với `noeviction`, 0 không chứng minh Redis vẫn còn chỗ để ghi |
| Postgres / Redis CPU | CPU container. Kết hợp latency/traffic/RAM trước khi kết luận cần scale |

Không có một ngưỡng heap/latency/pool đúng cho mọi workload. Dùng baseline khi
bình thường, so sánh thay đổi và các chỉ số liên quan; ngưỡng alert nằm trong
`infra/lgtm/provisioning/alerting/rules.yaml`.

### JVM Overview và RED mặc định LGTM

| Biểu đồ | Cách đọc |
|---|---|
| JVM Rate | Lưu lượng HTTP server, request/giây theo instance |
| JVM Error % | Tỷ lệ lỗi HTTP server; phải xem unit và query để phân biệt giá trị 0–1 với 0–100 |
| JVM Duration (95%) | p95: khoảng 95% request nằm dưới độ trễ đó; không phải trung bình |
| JVM CPU utilization | CPU process JVM theo metric của agent; không mặc định cùng đơn vị/scope với Docker CPU |
| JVM Heap Memory utilization | Mức dùng heap; so sánh với GC và response latency |
| JVM Garbage Collection | Tỷ lệ/thời gian GC theo query agent, xem description và đơn vị panel |
| JVM Classes / Threads | Class loaded và số thread sống theo instance |
| JVM Common Java logs | Gộp ba Java service/mọi replica. Job/Instance selector của metrics không lọc log chung |
| RED Request Rate | Lưu lượng request theo service/instance |
| RED Error Rate | Tốc độ/tỷ lệ lỗi theo query của panel; xem đơn vị trước khi đọc như phần trăm |
| RED Duration histogram / heatmap | Phân bố latency theo thời gian; màu đậm hơn là nhiều request ở bucket đó |
| RED Duration percentiles | p50/p95/p99. p99 cao hơn nhiều p50 cho thấy có nhóm request chậm |

Classic và native histogram là hai kiểu dữ liệu khác nhau. Native dashboard có
thể No data khi exporter/agent chỉ gửi classic; không có nghĩa mọi service chết.
Nếu biểu đồ phần trăm vượt mức vô lý, kiểm tra unit/scale và mẫu số của query;
không xem đó là tỷ lệ lỗi nghiệp vụ chính xác khi chưa đối chiếu request.

## 3. Container có tự restart không?

Compose hiện dùng `restart: unless-stopped`: Docker có thể khởi động lại process
đã thoát; container bị stop chủ động sẽ giữ trạng thái dừng. Policy không tạo lại
container đã bị `compose down` xóa. Xem [Docker restart policies](https://docs.docker.com/engine/containers/start-containers-automatically/).

Healthcheck đánh dấu readiness; **`unhealthy` không tự restart process còn sống**.
`depends_on: service_healthy` điều phối startup, không tự chữa lỗi DB xảy ra sau đó.
Xem [Compose startup order](https://docs.docker.com/compose/how-tos/startup-order/).

| Tình huống | Hành vi trong project |
|---|---|
| Java process tự crash | Docker restart policy xử lý; kiểm tra RestartCount, logs, exit/OOM |
| API treo nhưng process còn sống | Có thể unhealthy; Compose mặc định không restart chỉ vì health |
| `docker compose restart` | Restart thủ công cùng container, thời điểm StartedAt đổi; Auto restarts có thể vẫn 0 |
| Rebuild/recreate | Container ID mới, counter mới; không cộng lịch sử của container đã xóa |
| `docker compose stop` / down | Dừng chủ động; cần start/up khi muốn chạy lại |
| Watchdog tùy chọn restart | Script dùng Docker restart chủ động, không được hiểu như counter auto restart |

**Watchdog hiện đã bật trên máy local bằng LaunchAgent.** Deploy trên macOS
khởi động/cập nhật LaunchAgent; trên VPS Linux cần installer systemd riêng. `scripts/health-watchdog.py` là tool
host-side riêng: poll mặc định 10 giây, unhealthy liên tục 30 giây mới restart,
cooldown 90 giây; chỉ can thiệp `modular-app`, `gateway`, `eureka`, không restart DB.
Trên Linux/VPS, khi chủ động muốn bật, xem `scripts/install-watchdog.sh` và
`infra/systemd/ddd-watchdog.service.example`; installer tạo bản root-owned riêng
rồi bật systemd service. Không chạy installer Linux trên macOS.
Sau khi bật, kiểm tra `systemctl status ddd-watchdog` và `journalctl -u ddd-watchdog`.
Không chạy watchdog trực tiếp từ checkout mà CI/user khác có thể sửa với quyền root.

Compose không phải Kubernetes controller: không tự tạo replica thay thế đã bị xóa,
không tự autoscale và không đưa service sang host khác. `Auto restarts=0` cũng không
chứng minh chưa từng có thao tác restart/redeploy. Watchdog restarts lưu riêng bền vững qua recreate; không tính thao tác
stop/start thủ công hoặc auto restart của Docker. Không có một bộ đếm cộng tất cả
nguồn restart. Thời điểm start và container ID là dữ liệu để đối chiếu.

## 4. Lệnh kiểm tra thực tế

Chạy trong thư mục project. Các lệnh inspect dưới đây chỉ xuất trạng thái cần thiết,
không dump toàn bộ environment/secret.

```bash
# Bao gồm container đang dừng; kiểm tra đủ 3 app.
docker compose ps -a

# Kiểm tra từng replica (thay NAME bằng tên lấy từ compose ps).
docker inspect NAME --format 'status={{.State.Status}} running={{.State.Running}} exit={{.State.ExitCode}} oom={{.State.OOMKilled}} autoRestarts={{.RestartCount}} started={{.State.StartedAt}}'
docker inspect NAME --format '{{if .State.Health}}{{.State.Health.Status}}{{else}}no-healthcheck{{end}}'
docker inspect NAME --format '{{if .State.Health}}{{range .State.Health.Log}}{{.End}} exit={{.ExitCode}} {{.Output}}{{end}}{{end}}'

# Log lỗi mới nhất và kiểm tra Actuator một replica.
docker compose logs --since 10m --tail 100 modular-app
docker exec NAME curl -fsS http://127.0.0.1:8080/actuator/health

# Khi muốn chạy lại service đã stop.
docker compose up -d --no-build --scale modular-app=3 --wait --wait-timeout 120

# Restart chủ động; không thay thế deploy code mới.
./scripts/deploy.sh restart app

# Deploy source mới, giữ 3 replica.
./scripts/deploy.sh up 3
```

Nếu host yếu: `docker stats --no-stream`, kiểm tra OOM và RAM host trước khi tăng
pool/thread/replica. Hikari max 8 * 3 replica chưa tính các DB connection ngoài app.
Nếu vừa sửa `.env`, restart đơn thuần không áp dụng environment mới; cần recreate
bằng `compose up` hoặc deployment phù hợp.

## 5. Quy trình xử lý các lỗi thường gặp

### Service không start hoặc cứ chết

1. `compose ps -a`: pending/starting/exited/restarting?
2. Inspect ExitCode/OOMKilled và StartedAt; đọc log startup đúng container.
3. Xác nhận DB/Redis healthy, cấu hình/key đọc được, migration đã chạy.
4. Với fresh DB: tạo user/quyền bằng SQL riêng `.local/db-users.sql` trước app;
   deploy không còn tự grant. App/Liquibase dùng `run`, backup dùng tài khoản đọc.
5. Restart chỉ sau khi hiểu nguyên nhân; không lặp restart để che lỗi migration/config.

### App healthy nhưng request thất bại

Kiểm tra HTTP status: 401/403 liên quan token/quyền/origin; 429 liên quan rate limit;
502/503 có thể gateway/discovery/backend chưa sẵn sàng; 500 cần exception và trace.
Kiểm tra Eureka đã thấy cả ba replica và gateway có route đến app.
Đối chiếu pending Hikari, DB connection, HTTP 5xx và duration, không chỉ nhìn CPU.

### Scrape lỗi hoặc nhiều panel No data

Kiểm tra LGTM, observer, Docker socket proxy rồi Actuator/exporters. Phân biệt
`ddd_observer_scrape_success` (đọc Docker) với `ddd_metrics_scrape_success` (đọc
endpoint metrics). App có thể hoạt động trong khi monitoring bị lỗi và ngược lại.
Mỗi JVM scrape row là sample mới nhất theo container; No data khác 0.

### Không thấy logs/traces

Alloy chỉ đọc log stdout/stderr ba Java service và index `service_name`. OTel bật
chưa đủ để có log nếu Logback đang chỉ ghi file. `file-logs` là fallback tùy chọn.
So sánh log Docker với Loki. Trace cần request được instrument/sample; dùng time
range tương ứng và trace ID trong log. Không suy luận trace No data = DB chết.

## 6. Backup và cảnh báo

Backup PostgreSQL chạy ngay khi container start rồi vào đầu mỗi giờ. Giữ 7 ngày;
chỉ prune bản quá hạn sau dump mới được kiểm tra và xuất bản thành công. Lỗi dump
không xóa backup hợp lệ trước đó. Health backup kiểm tra tuổi marker và checksum;
trạng thái healthy không có nghĩa đã kiểm chứng mọi restore nghiệp vụ.

Telegram backup độc lập Grafana, có retry/outbox và template tiếng Việt ở
`infra/lgtm/resources/backup-notification-template.json`. Grafana alert dùng rules
và notification resources riêng. RestartCount=0 không phải bằng chứng đã nhận
Telegram; phải đối chiếu tin nhắn và trạng thái backup.

Không xóa `.last-success`, lock/outbox/cursor của backup hoặc `.deploy/state.env`
chỉ để dọn thư mục. Xóa state replica có thể khiến observer hiểu sai desired.
Bản dump cùng host không bảo vệ khỏi mất cả ổ đĩa; khi lên VPS cần chính sách
sao lưu ngoài host và kiểm tra restore riêng.

## 7. Các phép kiểm tra đã thực hiện ở local

- Gọi GET Catalog với tài khoản test: HTTP 200; audit xuất hiện trong Docker và
  truy vấn Loki. Session test được logout sau kiểm tra, không ghi token vào tài liệu.
- Bảng scrape trả đúng 5 container Java (3 app, gateway, Eureka), mỗi container một dòng.
- Cho process Redis exporter thoát bằng SIGTERM bên trong container: Docker tự
  khởi động lại cùng container, RestartCount tăng 0 → 1 và health trở lại healthy.
  Không dùng `compose restart` hoặc `compose up` để kích hoạt lần phục hồi này;
  Redis và các app tiếp tục chạy. Đây là bằng chứng cho restart policy khi process
  thoát, không phải bằng chứng tự restart một process còn sống nhưng unhealthy.
- Đây là snapshot kiểm tra; counter có thể reset khi recreate container.

## 8. Watchdog trong bảng và lệnh tương tự kiểm tra Kubernetes

Hai bảng Operations/Observability có thêm:

| Cột / panel | Ý nghĩa |
|---|---|
| Watchdog restarts | Số lệnh restart watchdog thực hiện thành công, lưu theo service qua recreate |
| Watchdog gần nhất | Thời điểm watchdog thử restart, kể cả lệnh thất bại |
| Kết quả phục hồi | Chưa thử / đang chờ health / đã phục hồi / restart lỗi / quá hạn / đã dừng / container bị xóa |
| Host watchdog | Active: heartbeat <90 giây; offline: không thấy daemon hoạt động. Không suy luận từ số restart cũ rằng daemon đang chạy |

Counter tăng khi Docker chấp nhận lệnh restart, **kết quả phục hồi** mới xác nhận
health đã trở lại. Watchdog đợi tối đa 180 giây sau lần thử; failure/timeout không
được gắn nhãn thành công. Cooldown theo service 90 giây vẫn áp dụng khi daemon
chạy lại. Log JSON chứa service, container ID, timestamp và kết quả; không lưu
Docker environment/password. State/counter không được xóa như file rác.

Điều kiện: Running=true, không paused/restarting, health=`unhealthy` liên tục
30 giây quan sát được. `starting` có grace của Docker healthcheck (Java hiện 80s),
không bị restart chỉ vì chưa healthy sau 30 giây. Process đã thoát do restart policy
của Docker xử lý; stop chủ động giữ dừng. Không restart PostgreSQL/Redis. Khi host
watchdog restart, cửa sổ unhealthy liên tục được đo lại để tránh restart dựa trên
snapshot cũ. Việc stop ngay sau lần kiểm tra trước restart vẫn là race của thao tác
Docker; muốn bảo trì hãy dừng watchdog trước, rồi stop service.

macOS (đã bật cho local):

```bash
./scripts/watchdog.sh status
./scripts/watchdog.sh history 30
./scripts/watchdog.sh stop   # bảo trì chủ động
./scripts/watchdog.sh start  # bật lại và cập nhật bản runtime
```

LaunchAgent chạy theo user, bản runtime/state nằm ở
`~/Library/Application Support/ddd-platform/watchdog/`; tránh macOS chặn Downloads.
`WATCHDOG_STATE_DIR` trong `.env` chỉ đường dẫn state để observer mount read-only.
LaunchAgent khởi động theo phiên đăng nhập, không phải daemon hệ thống trước login.
Watchdog và observer dùng hai process riêng: observer không được quyền restart Docker.

Linux/VPS:

```bash
sudo ./scripts/install-watchdog.sh
# Đặt WATCHDOG_STATE_DIR=/var/lib/ddd-platform-watchdog trong .env.
docker compose up -d --no-build ops-observer
sudo systemctl status ddd-watchdog
sudo journalctl -u ddd-watchdog --since '15 minutes ago'
sudo tail -n 30 /var/lib/ddd-platform-watchdog/events.jsonl
sudo cat /var/lib/ddd-platform-watchdog/state.json
```

State systemd được giữ trong `/var/lib/ddd-platform-watchdog`, runtime riêng
root-owned trong `/usr/local/libexec/ddd-platform`; không cấp root cho code checkout.
Không dùng câu lệnh `kubectl` vì project chưa deploy Kubernetes. Lệnh tương ứng:
`compose ps -a` để xem container, `docker inspect` để đọc health/exit/StartedAt,
`compose logs` để xem exception, history watchdog để biết ai thử phục hồi và kết quả.
Không có distributed trace nghiệp vụ cho thao tác restart host; lịch sử watchdog
là event vận hành, không đưa vào Loki chỉ dành cho log Java. Khi crash giữa việc
Docker restart và ghi counter có thể thiếu một lần; đây không phải audit exactly-once.

Kiểm chứng watchdog local: tạo lỗi probe healthcheck có kiểm soát trên một
replica app, watchdog thực hiện restart, probe được khôi phục, app healthy lại.
Counter watchdog app tăng 0 → 1 và kết quả là recovered; hai replica khác
không bị restart. Đây là thử luồng remediation, không khẳng định mọi lỗi
nghiệp vụ sẽ được restart giải quyết.

## 9. Telegram: lỗi 5xx, exception và mất dữ liệu

Rule 5xx: rate 5 phút >0.1 request/giây, giữ liên tục 2 phút. Không gửi chỉ vì
một request 5xx; không tính route `/actuator.*` vì health có rule riêng.
Khi query mất dữ liệu, rule giữ trạng thái trước (`KeepLast`); monitoring mất
metrics được theo dõi bởi rule scrape/observer và trạng thái datasource riêng.

Rule Java ERROR đọc log ba Java service mỗi phút: có log ERROR phù hợp trong
5 phút thì cảnh báo; loại `api.exception status=4xx` để không coi lỗi client
là sự cố server. Đây là rule độc lập, một exception ERROR có thể gửi Telegram dù
chưa đạt ngưỡng rate 5xx. Một trace có nhiều dòng được đếm theo service, exception,
vị trí và trace ID. Các nhóm alert dùng policy hiện tại (group_wait 30s,
group_interval 5m, repeat_interval 4h); không gửi một message cho từng dòng log.

Tin có service, exception class, vị trí stacktrace, trace ID nếu có, giá trị
đánh giá và link panel/rule. Log thiếu trace không được tạo trace ID giả. `Unknown`
có nghĩa ERROR không có loại exception trong stacktrace. Stacktrace đầy đủ đọc
qua panel Common Java logs ở Observability; trace ID tra ở Tempo Explore.
Không gửi raw exception message/body/password/token qua Telegram. Template chỉ
hiển thị tối đa 3 alert đầu trong nhóm; phần còn lại xem Grafana. Cửa sổ 5 phút
không còn ERROR là kết thúc cảnh báo quan sát, không chứng minh bug đã được sửa.

`NoData`, `DatasourceError` và `MissingSeries` là vấn đề dữ liệu/evaluation,
không phải exception nghiệp vụ. Tin thiếu service trong trường hợp này phải báo
không xác định, không suy đoán tên app. `MissingSeries` có thể kết thúc alert vì
series bị loại khỏi evaluation chứ không phải vì sửa xong lỗi API.

Đối chiếu tin ngày 14/10/2026: lịch sử Grafana của rule 5xx ghi
16:37:50 Normal → Pending (NoData), 16:39:50 Pending (NoData) → NoData,
16:40:50 NoData → Normal (MissingSeries). Query 5xx trong đoạn có sample là 0,
không thấy HTTP audit 5xx tương ứng. Vì vậy tin này là cảnh báo mất metrics được
trình bày nhầm như HTTP 5xx, không có exception API để gắn vào. Chưa xác định
được thành phần gây gap metrics chỉ từ lịch sử này; không gán cho lỗi heartbeat
Eureka lúc 15:55 (khác thời điểm).

## 10. Cách tái hiện rule và phạm vi kiểm chứng

Một phép thử end-to-end cần chứng minh: dữ liệu đầu vào thực sự xuất hiện → query
vượt ngưỡng → Pending đủ thời gian → Firing → notification Telegram thành công →
phục hồi dữ liệu → Normal. Test render template hoặc POST gửi Telegram riêng chỉ
kiểm chứng định dạng/transport, không chứng minh rule tự phát hiện sự cố.

Ngày 14/10/2026 đã thử API thật bằng cách tạm đổi tên bảng Catalog (không xóa dữ
liệu), gửi request GET có authentication trong 5 phút rồi đổi tên lại. Kết quả:
288 response 503; log `InvalidDataAccessResourceUsageException`, SQL báo relation
catalog_item không tồn tại, có trace ID. Rule exception và rule 5xx đều Firing.
Sau restore, GET Catalog trả 200. Lỗi này không đổi JWT/quyền và session test được
logout. Khi áp dụng cách thử này phải có cơ chế finally/rollback để phục hồi tên
bảng, không làm trên production.

Để test các rule tài nguyên, tạo Prometheus + exporter fixture riêng và bản sao
rule `TEST LOCAL`, datasource riêng, giữ nguyên query, ngưỡng và pending duration.
Đưa fixture từ normal sang fault rồi về normal. Rule test được gắn `test_run` và
summary TEST FIXTURE. Cách này kiểm chứng logic evaluation/notification mà không
thực sự làm heap đầy, CPU quá tải hoặc DB deadlock; không được ghi nhận như đã
kiểm thử sự cố vật lý của hệ thống. Sau thử phải xóa group test, datasource và
container fixture; rule thật vẫn giữ nguyên.

| Rule | Tín hiệu để tái hiện | Thời gian giữ điều kiện |
|---|---|---|
| `ddd-app-replicas-zero` | Running app = 0 | 1m |
| `ddd-gateway-down` | Running gateway = 0 | 1m |
| `ddd-postgres-down` | pg_up = 0 | 1m |
| `ddd-redis-down` | redis_up = 0 | 1m |
| `ddd-app-missing-replicas` | Healthy app < Desired | 3m |
| `ddd-jvm-high-heap` | Heap used/max >85% | 5m |
| `ddd-hikari-high-usage` | Hikari active/max >85% | 3m |
| `ddd-postgres-high-connections` | Database connections >40 | 5m |
| `ddd-redis-high-memory` | Redis used/max >85% | 5m |
| `ddd-postgres-deadlocks` | Counter deadlock tăng trong cửa sổ 10 phút | 1m |
| `ddd-containers-cpu-high` | CPU >90% một core | 5m |
| `ddd-exporter-scrape-failed` | Scrape success = 0 | 2m |
| `ddd-observer-down` | Observer success = 0 | 2m |
| `ddd-nginx-down` | Running nginx = 0 | 2m |
| `ddd-eureka-down` | Running Eureka = 0 | 2m |
| `ddd-hikari-pending` | Hikari pending >0 | 2m |
| `ddd-http-5xx` | Rate 5xx >0.1/s trong cửa sổ 5 phút | 2m |
| `ddd-java-exception` | Một log ERROR exception phù hợp trong 5 phút | 0s |

Rate/increase có cửa sổ nhìn về quá khứ: API hết lỗi chưa làm rate 5 phút trở về 0
ngay; counter deadlock ngừng tăng chưa làm increase 10 phút về 0 ngay. Chờ hết cửa
sổ rồi đối chiếu Normal, không reset rule để giả lập tin phục hồi. Telegram có
`group_wait=30s` và `group_interval=5m`, nên thay đổi state và lúc nhận tin không
nhất thiết cùng giây. Trạng thái resolved chỉ chứng minh điều kiện không còn
vượt ngưỡng ở lần evaluation, không phải bằng chứng mọi bug đã được sửa.

Phép thử tạo nhiều trace làm tin Telegram vượt giới hạn 4096 ký tự. Template đã
được giảm còn 3 chi tiết/nhóm; render bằng 6 alert thật dài 2736 ký tự, không bị
cắt mất link điều tra. Phần còn lại xem trong Grafana.

Kết quả cuối của lượt thử: 17/17 bản sao metrics chuyển Normal → Pending →
Alerting → Normal, 0 transition do NoData/Error. Hai rule thật exception/5xx
đều Firing rồi về Normal sau khi bảng được phục hồi và hết cửa sổ 5 phút.
Grafana ghi nhận các lượt gửi Telegram trong lượt thử tăng từ 3 lên 42;
không ghi nhận lỗi gửi ở các lượt mới. Đây là xác nhận phía Grafana/transport,
không khẳng định người nhận đã đọc đủ mọi tin. Tất cả 17 rule thử, datasource
TEST và hai container fixture đã được xóa; còn đúng 18 rule thật và 4 datasource
LGTM mặc định. Các ngưỡng và pending duration thật không bị giảm để test.
