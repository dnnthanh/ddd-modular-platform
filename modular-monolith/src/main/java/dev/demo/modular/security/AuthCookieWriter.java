package dev.demo.modular.security;

import jakarta.servlet.http.HttpServletResponse;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AuthCookieWriter {
  public static final String REFRESH_COOKIE = "refresh_token";
  private final AuthCookieProperties properties;

  public void writeRefreshToken(HttpServletResponse response, String value, long maxAgeSeconds) {
    ResponseCookie.ResponseCookieBuilder builder =
        ResponseCookie.from(REFRESH_COOKIE, value)
            .httpOnly(true)
            .secure(properties.secure())
            .sameSite(properties.sameSite())
            .path(properties.path())
            .maxAge(maxAgeSeconds);
    if (Objects.nonNull(properties.domain()) && !properties.domain().trim().isEmpty()) {
      builder.domain(properties.domain());
    }
    response.addHeader("Set-Cookie", builder.build().toString());
  }

  public void clearRefreshToken(HttpServletResponse response) {
    writeRefreshToken(response, "", 0);
  }
}
