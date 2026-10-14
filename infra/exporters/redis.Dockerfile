# The upstream exporter is scratch-based and has no HTTP probe utility.
# Add one static BusyBox binary; keep the upstream exporter and its non-root user.
FROM busybox:1.37.0-musl AS health-tools
FROM oliver006/redis_exporter:v1.93.0
COPY --from=health-tools /bin/busybox /bin/busybox
