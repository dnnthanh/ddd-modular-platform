# OpenTelemetry Java agent (local dependency)

Pinned version: **2.21.0**. Docker copies this JAR to `/opt/otel/javaagent.jar`;
no agent download occurs during image builds.

Source: https://repo.maven.apache.org/maven2/io/opentelemetry/javaagent/opentelemetry-javaagent/2.21.0/opentelemetry-javaagent-2.21.0.jar

The SHA-256 was checked against the adjacent Maven Central `.jar.sha256` file.
Verify the local artifact before building:

```bash
(cd libs/opentelemetry && shasum -a 256 -c SHA256SUMS)
```

To update, deliberately download a specific released version, verify its upstream
checksum, update `SHA256SUMS` and the Dockerfile COPY filename, and rerun verification.
Keep the JAR in the project when copying or checking out this directory.
Maven application dependencies and base images still require their normal caches.

License: Apache-2.0 (upstream distribution).
