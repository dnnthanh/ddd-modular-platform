package dev.demo.core.web;

import dev.demo.core.exception.ErrorCategory;
import java.util.Map;
import java.util.Objects;

public record ApiFailure(
    String code, ErrorCategory category, String message, String path, Map<String, String> details) {
  public ApiFailure {
    details = Objects.isNull(details) ? Map.of() : Map.copyOf(details);
  }
}
