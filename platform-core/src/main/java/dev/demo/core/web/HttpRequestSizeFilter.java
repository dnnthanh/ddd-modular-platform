package dev.demo.core.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Objects;
import org.jspecify.annotations.NonNull;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/** Bounds JSON API input independently of proxy and audit-preview limits. */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 5)
public class HttpRequestSizeFilter extends OncePerRequestFilter {
  private final int limit;

  public HttpRequestSizeFilter(@Value("${app.security.http.max-request-bytes:1048576}") int limit) {
    if (limit < 1 || limit > 67108864)
      throw new IllegalArgumentException("Invalid API request size limit");
    this.limit = limit;
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, @NonNull HttpServletResponse response, @NonNull FilterChain chain)
      throws ServletException, IOException {
    String contentType = request.getContentType();
    if (!request.getRequestURI().startsWith("/api/")
        || Objects.isNull(contentType)
        || !contentType.toLowerCase(Locale.ROOT).contains("json")) {
      chain.doFilter(request, response);
      return;
    }
    if (request.getContentLengthLong() > limit) {
      reject(response);
      return;
    }
    byte[] body = request.getInputStream().readNBytes(limit + 1);
    if (body.length > limit) {
      reject(response);
      return;
    }
    var buffered =
        new HttpServletRequestWrapper(request) {
          @Override
          public ServletInputStream getInputStream() {
            var bytes = new ByteArrayInputStream(body);
            return new ServletInputStream() {
              @Override
              public int read() {
                return bytes.read();
              }

              @Override
              public int read(byte @NonNull [] target, int offset, int length) {
                return bytes.read(target, offset, length);
              }

              @Override
              public boolean isFinished() {
                return bytes.available() == 0;
              }

              @Override
              public boolean isReady() {
                return true;
              }

              @Override
              public void setReadListener(ReadListener listener) {
                try {
                  if (!isFinished()) listener.onDataAvailable();
                  if (isFinished()) listener.onAllDataRead();
                } catch (IOException exception) {
                  listener.onError(exception);
                }
              }
            };
          }

          @Override
          public BufferedReader getReader() {
            String encoding = getCharacterEncoding();
            return new BufferedReader(
                new InputStreamReader(
                    getInputStream(),
                    Objects.isNull(encoding) ? StandardCharsets.UTF_8 : Charset.forName(encoding)));
          }
        };
    chain.doFilter(buffered, response);
  }

  private void reject(HttpServletResponse response) throws IOException {
    response.setStatus(413);
    response.setContentType(MediaType.APPLICATION_JSON_VALUE);
    response
        .getWriter()
        .write(
            "{\"success\":false,\"error\":{\"code\":\"PAYLOAD_TOO_LARGE\",\"message\":\"Request body exceeds the configured limit\"}}");
  }
}
