package dev.demo.core.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class HttpRequestSizeFilterTest {
  @Test
  void rejectsOversizedJsonBeforeControllerAndPreservesAllowedBody() throws Exception {
    var filter = new HttpRequestSizeFilter(4);
    var request = new MockHttpServletRequest("POST", "/api/public/auth/login");
    request.setContentType("application/json");
    request.setContent("12345".getBytes());
    var response = new MockHttpServletResponse();
    var invoked = new AtomicBoolean();
    filter.doFilter(request, response, (req, res) -> invoked.set(true));
    assertEquals(413, response.getStatus());
    assertFalse(invoked.get());
    request = new MockHttpServletRequest("POST", "/api/public/auth/login");
    request.setContentType("application/json");
    request.setContent("1234".getBytes());
    filter.doFilter(
        request,
        new MockHttpServletResponse(),
        (req, res) -> {
          assertEquals("1234", new String(req.getInputStream().readAllBytes()));
          invoked.set(true);
        });
    assertTrue(invoked.get());
  }

  @Test
  void unknownLengthBodyCannotBypassLimit() throws Exception {
    var request =
        new MockHttpServletRequest("POST", "/api/public/auth/login") {
          @Override
          public long getContentLengthLong() {
            return -1;
          }

          @Override
          public int getContentLength() {
            return -1;
          }
        };
    request.setContentType("application/json");
    request.setContent("12345".getBytes());
    var response = new MockHttpServletResponse();
    new HttpRequestSizeFilter(4)
        .doFilter(
            request,
            response,
            (req, res) -> {
              throw new AssertionError("oversized body reached controller");
            });
    assertEquals(413, response.getStatus());
  }
}
