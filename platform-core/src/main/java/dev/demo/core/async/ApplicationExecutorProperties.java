package dev.demo.core.async;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.executors.application")
public record ApplicationExecutorProperties(int coreSize, int maxSize, int queueCapacity) {}
