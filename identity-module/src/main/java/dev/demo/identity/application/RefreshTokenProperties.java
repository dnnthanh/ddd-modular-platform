package dev.demo.identity.application;

import java.time.Duration;
import java.util.Objects;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.security.refresh")
public record RefreshTokenProperties(Duration ttl) {
  public RefreshTokenProperties {
    if (Objects.isNull(ttl) || ttl.isNegative() || ttl.isZero()) {
      throw new IllegalArgumentException("app.security.refresh.ttl must be positive");
    }
  }
}
