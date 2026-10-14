package dev.demo.modular.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URI;
import java.util.Arrays;
import java.util.Objects;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Origin validation is required because refresh credentials are cookies and no CSRF token is
 * readable.
 */
@Component
@RequiredArgsConstructor
public class RefreshCsrfFilter extends OncePerRequestFilter {
  private static final String LEGACY_LOGOUT_PATH = "/api/private/auth/logout";
  private static final String LOGIN_PATH = "/api/public/auth/login";
  private static final Set<String> COOKIE_PATHS =
      Set.of("/api/public/auth/refresh", "/api/public/auth/logout");
  private final AuthCookieProperties properties;

  @Override
  protected boolean shouldNotFilter(HttpServletRequest request) {
    return !HttpMethod.POST.matches(request.getMethod())
        || (!LOGIN_PATH.equals(request.getRequestURI())
            && !COOKIE_PATHS.contains(request.getRequestURI())
            && !LEGACY_LOGOUT_PATH.equals(request.getRequestURI()));
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    String supplied = suppliedOrigin(request);
    boolean browserCookieRequest =
        COOKIE_PATHS.contains(request.getRequestURI())
            || (LEGACY_LOGOUT_PATH.equals(request.getRequestURI()) && hasRefreshCookie(request));
    if ((browserCookieRequest && Objects.isNull(supplied))
        || (Objects.nonNull(supplied) && !allowed(supplied))) {
      throw new AccessDeniedException("Cross-origin refresh request denied");
    }
    chain.doFilter(request, response);
  }

  private static boolean hasRefreshCookie(HttpServletRequest request) {
    if (Objects.isNull(request.getCookies())) return false;
    return Arrays.stream(request.getCookies())
        .anyMatch(cookie -> "refresh_token".equals(cookie.getName()));
  }

  private boolean allowed(String supplied) {
    return properties.allowedOrigins().stream().anyMatch(supplied::equalsIgnoreCase);
  }

  private static String suppliedOrigin(HttpServletRequest request) {
    String origin = request.getHeader(HttpHeaders.ORIGIN);
    return Objects.isNull(origin) || origin.trim().isEmpty()
        ? origin(request.getHeader(HttpHeaders.REFERER))
        : origin;
  }

  private static String origin(String referer) {
    try {
      URI uri = URI.create(referer);
      return uri.getScheme() + "://" + uri.getAuthority();
    } catch (RuntimeException exception) {
      return null;
    }
  }
}
