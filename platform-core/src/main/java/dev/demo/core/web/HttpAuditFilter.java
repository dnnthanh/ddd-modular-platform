package dev.demo.core.web;

import dev.demo.core.observability.TraceContext;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingRequestWrapper;

/** Logs safe request/response previews with bounded memory and no raw credentials. */
@Slf4j
@Component
@RequiredArgsConstructor
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class HttpAuditFilter extends OncePerRequestFilter {

  private final HttpAuditProperties properties;
  private final HttpAuditSanitizer sanitizer;
  private final AntPathMatcher matcher = new AntPathMatcher();

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, @NonNull HttpServletResponse response, @NonNull FilterChain chain)
      throws ServletException, IOException {
    String path = request.getRequestURI();
    if (!properties.isEnabled()
        || properties.getExcludedPaths().stream()
            .anyMatch(pattern -> matcher.match(pattern, path))) {
      chain.doFilter(request, response);
      return;
    }

    int limit = Math.clamp(properties.getMaxBodyBytes(), 1, 65536);
    var wrappedRequest = new ContentCachingRequestWrapper(request, limit);
    var wrappedResponse = new BoundedResponseCapture(response, limit);
    long start = System.nanoTime();
    boolean threw = false;
    try {
      chain.doFilter(wrappedRequest, wrappedResponse);
    } catch (IOException | ServletException | RuntimeException ex) {
      threw = true;
      throw ex;
    } finally {
      // Do not accidentally break the HTTP response due to audit failures.
      try {
        wrappedResponse.flushWriter();
        int status = threw && wrappedResponse.getStatus() < 400 ? 500 : wrappedResponse.getStatus();
        boolean captureBodies =
            properties.isLogBodies()
                && !request.isAsyncStarted()
                && properties.getBodyPaths().stream()
                    .anyMatch(pattern -> matcher.match(pattern, path));
        String requestBody =
            captureBodies
                ? sanitizer.body(
                    wrappedRequest.getContentAsByteArray(),
                    request.getContentType(),
                    wrappedRequest.getContentAsByteArray().length >= limit)
                : "[DISABLED]";
        String responseBody =
            captureBodies
                ? sanitizer.body(
                    wrappedResponse.capturedBytes(),
                    wrappedResponse.getContentType(),
                    wrappedResponse.isTruncated())
                : "[DISABLED]";

        log.atInfo()
            .addKeyValue("event", "http.audit")
            .addKeyValue("http_method", request.getMethod())
            .addKeyValue("http_path", path.replaceAll("[\r\n]", ""))
            .addKeyValue(
                "http_query",
                sanitizer.query(
                    request.getQueryString(), Math.max(1, properties.getMaxQueryChars())))
            .addKeyValue("http_status", status)
            .addKeyValue("duration_ms", (System.nanoTime() - start) / 1_000_000L)
            .addKeyValue("request_body", requestBody)
            .addKeyValue("response_body", responseBody)
            .addKeyValue("trace_id", TraceContext.traceId().orElse(""))
            .log("HTTP request completed");
      } catch (RuntimeException auditError) {
        log.warn("HTTP audit unavailable: {}", auditError.getClass().getSimpleName());
      }
    }
  }
}
