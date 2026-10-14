package dev.demo.gateway;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.gateway.rate-limit")
public record GatewayRateLimitProperties(
    int replenishRate, int burstCapacity, int requestedTokens) {}
