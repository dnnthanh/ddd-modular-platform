package dev.demo.core.web;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Strings;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

/** Fail-closed JSON sanitization: unknown/non-JSON payloads are omitted. */
@Component
@RequiredArgsConstructor
public class HttpAuditSanitizer {

  private static final String REDACTED = "[REDACTED]";
  private static final String OMITTED = "[OMITTED]";
  private static final List<String> SENSITIVE =
      List.of(
          "password",
          "passwd",
          "secret",
          "token",
          "authorization",
          "cookie",
          "credential",
          "apikey",
          "privatekey",
          "signature",
          "session",
          "otp",
          "pin",
          "email",
          "phone",
          "address",
          "cardnumber",
          "cvv",
          "ssn");

  private final ObjectMapper objectMapper;

  public String query(String rawQuery, int maxChars) {
    if (StringUtils.isBlank(rawQuery)) {
      return "";
    }
    if (rawQuery.length() > maxChars) {
      return "[OMITTED:query-too-long]";
    }
    Map<String, Object> safe = new LinkedHashMap<>();
    for (String pair : rawQuery.split("&", -1)) {
      String[] entry = pair.split("=", 2);
      try {
        String name = URLDecoder.decode(entry[0], StandardCharsets.UTF_8);
        String value = entry.length > 1 ? URLDecoder.decode(entry[1], StandardCharsets.UTF_8) : "";
        safe.put(name, isSensitive(name) ? REDACTED : value);
      } catch (IllegalArgumentException _) {
        return "[OMITTED:invalid-query]";
      }
    }
    return toJson(safe);
  }

  public String body(byte[] bytes, String contentType, boolean truncated) {
    if (bytes.length == 0) {
      return "";
    }
    if (truncated) {
      return "[OMITTED:body-too-large]";
    }
    if (!Strings.CS.contains(contentType, "json")) {
      return "[OMITTED:non-json]";
    }
    try {
      Object decoded = objectMapper.readValue(bytes, Object.class);
      return toJson(redact(decoded, 0));
    } catch (RuntimeException _) {
      return "[OMITTED:invalid-json]";
    }
  }

  private Object redact(Object value, int depth) {
    if (depth > 12) {
      return OMITTED;
    }
    if (value instanceof Map<?, ?> map) {
      Map<String, Object> safe = new LinkedHashMap<>();
      map.forEach(
          (key, item) -> {
            String name = String.valueOf(key);
            safe.put(name, isSensitive(name) ? REDACTED : redact(item, depth + 1));
          });
      return safe;
    }
    if (value instanceof List<?> list) {
      List<Object> safe = new ArrayList<>(list.size());
      list.forEach(item -> safe.add(redact(item, depth + 1)));
      return safe;
    }
    return value;
  }

  private boolean isSensitive(String name) {
    String normalized = name.replaceAll("[^a-zA-Z0-9]", "").toLowerCase(Locale.ROOT);
    return SENSITIVE.stream().anyMatch(normalized::contains);
  }

  private String toJson(Object object) {
    try {
      return objectMapper.writeValueAsString(object);
    } catch (RuntimeException _) {
      return "[OMITTED:serialization-failed]";
    }
  }
}
