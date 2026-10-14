# Multi-stage, one Dockerfile for three executable modules. Build from project root.
FROM maven:3.9.11-eclipse-temurin-25 AS build
WORKDIR /src
COPY . .
ARG MODULE
RUN mvn -B -ntp -pl "${MODULE}" -am -DskipTests package && \
    mkdir -p /out && \
    find "/src/${MODULE}/target" -maxdepth 1 -name '*.jar' ! -name '*.original' ! -name '*-sources.jar' | head -1 | xargs -I{} cp {} /out/app.jar

FROM eclipse-temurin:25-jre
RUN apt-get update && apt-get install -y --no-install-recommends curl ca-certificates iproute2 \
    && rm -rf /var/lib/apt/lists/* \
    && useradd --system --uid 10001 --create-home app
WORKDIR /app
COPY --from=build /out/app.jar /app/app.jar
COPY libs/opentelemetry/opentelemetry-javaagent-2.21.0.jar /opt/otel/javaagent.jar
COPY --chmod=755 scripts/launch.sh /app/launch.sh
ARG MODULE
ARG RELEASE_TAG=dev
ARG GIT_REVISION=unknown
LABEL org.opencontainers.image.title="${MODULE}" \
      org.opencontainers.image.version="${RELEASE_TAG}" \
      org.opencontainers.image.revision="${GIT_REVISION}"
USER app
EXPOSE 8080 8761
ENTRYPOINT ["/app/launch.sh"]
