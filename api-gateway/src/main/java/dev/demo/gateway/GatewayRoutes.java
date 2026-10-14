package dev.demo.gateway;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.cloud.gateway.filter.ratelimit.RedisRateLimiter;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(GatewayRateLimitProperties.class)
public class GatewayRoutes {

  @Bean
  RedisRateLimiter redisRateLimiter(GatewayRateLimitProperties properties) {
    return new RedisRateLimiter(
        properties.replenishRate(), properties.burstCapacity(), properties.requestedTokens());
  }

  @Bean
  KeyResolver principalKeyResolver(
      @Value("${app.gateway.trusted-proxy-host:nginx}") String trustedHost) {
    return exchange ->
        exchange
            .getPrincipal()
            .map(principal -> "user:" + principal.getName())
            .switchIfEmpty(
                Mono.fromCallable(
                        () -> {
                          Set<String> trusted;
                          try {
                            // Resolve only the configured proxy host, never a user-supplied
                            // hostname.
                            trusted =
                                Arrays.stream(InetAddress.getAllByName(trustedHost))
                                    .map(InetAddress::getHostAddress)
                                    .collect(Collectors.toSet());
                          } catch (UnknownHostException exception) {
                            trusted = Set.of();
                          }
                          return AnonymousRateLimitKey.key(
                              exchange.getRequest().getRemoteAddress(),
                              exchange.getRequest().getHeaders().getFirst("X-Real-IP"),
                              trusted);
                        })
                    .subscribeOn(Schedulers.boundedElastic()));
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
