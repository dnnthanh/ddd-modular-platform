package dev.demo.core.web;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

class HttpAuditSanitizerTest {

  private final HttpAuditSanitizer sanitizer = new HttpAuditSanitizer(JsonMapper.builder().build());

  @Test
  void redactsNestedSecretsAndKeepsSafeFields() {
    String json =
        """
        {"sku":"A-001","nested":{"password":"abc","accessToken":"xyz"},
        "items":[{"name":"Coffee","email":"alice@example.com"}]}
        """;
    String result =
        sanitizer.body(
            json.getBytes(java.nio.charset.StandardCharsets.UTF_8), "application/json", false);

    assertTrue(result.contains("A-001"));
    assertTrue(result.contains("[REDACTED]"));
    assertFalse(result.contains("abc"));
    assertFalse(result.contains("xyz"));
    assertFalse(result.contains("alice@example.com"));
  }

  @Test
  void redactsEncodedQueryValuesAndRejectsTruncatedBody() {
    String result = sanitizer.query("q=coffee&access_token=abc123", 200);
    assertTrue(result.contains("coffee"));
    assertFalse(result.contains("abc123"));
    assertTrue(
        sanitizer
            .body("{}".getBytes(java.nio.charset.StandardCharsets.UTF_8), "application/json", true)
            .contains("OMITTED"));
  }
}
