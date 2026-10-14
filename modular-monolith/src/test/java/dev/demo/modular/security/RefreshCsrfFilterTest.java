package dev.demo.modular.security;

import static org.junit.jupiter.api.Assertions.assertThrows;

import jakarta.servlet.http.Cookie;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.access.AccessDeniedException;

class RefreshCsrfFilterTest {
  @Test
  void legacyLogoutWithCookieAlsoRequiresTrustedOrigin() {
    var filter =
        new RefreshCsrfFilter(
            new AuthCookieProperties(
                false, "Strict", "/api", "", List.of("http://localhost:8088")));
    var request = new MockHttpServletRequest("POST", "/api/private/auth/logout");
    request.setCookies(new Cookie("refresh_token", "test-only-cookie"));
    assertThrows(
        AccessDeniedException.class,
        () -> filter.doFilter(request, new MockHttpServletResponse(), (req, res) -> {}));
  }
}
