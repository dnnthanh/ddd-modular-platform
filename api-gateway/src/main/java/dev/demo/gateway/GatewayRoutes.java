package dev.demo.gateway;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.cloud.gateway.filter.ratelimit.RedisRateLimiter;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import reactor.core.publisher.Mono;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(GatewayRateLimitProperties.class)
public class GatewayRoutes {

  @Bean
  RedisRateLimiter redisRateLimiter(GatewayRateLimitProperties properties) {
    return new RedisRateLimiter(
        properties.replenishRate(), properties.burstCapacity(), properties.requestedTokens());
  }

  @Bean
  KeyResolver principalKeyResolver() {
    // Rate-limit by validated identity, not by untrusted request headers.
    return exchange ->
        exchange
            .getPrincipal()
            .map(principal -> principal.getName())
            .switchIfEmpty(Mono.just("anonymous"));
  }

  @Bean
  RouteLocator routes(
      RouteLocatorBuilder builder,
      RedisRateLimiter limiter,
      @Qualifier("principalKeyResolver") KeyResolver resolver) {
    return builder
        .routes()
        .route(
            "modular-app",
            route ->
                route
                    .path("/api/public/**", "/api/private/**")
                    .filters(
                        filters ->
                            filters.requestRateLimiter(
                                settings -> {
                                  settings.setRateLimiter(limiter);
                                  settings.setKeyResolver(resolver);
                                }))
                    .uri("lb://modular-app"))
        .build();
  }
}
