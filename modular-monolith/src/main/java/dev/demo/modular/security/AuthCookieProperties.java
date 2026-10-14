package dev.demo.modular.security;

import java.util.List;
import java.util.Objects;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.security.cookies")
public record AuthCookieProperties(
    boolean secure, String sameSite, String path, String domain, List<String> allowedOrigins) {
  public AuthCookieProperties {
    sameSite = Objects.isNull(sameSite) ? "Strict" : sameSite;
    path = Objects.isNull(path) ? "/api" : path;
    allowedOrigins = Objects.isNull(allowedOrigins) ? List.of() : List.copyOf(allowedOrigins);
    if (allowedOrigins.stream()
        .anyMatch(
            origin -> Objects.isNull(origin) || origin.trim().isEmpty() || origin.contains("*"))) {
      throw new IllegalArgumentException(
          "app.security.cookies.allowed-origins must contain explicit origins only");
    }
  }
}
