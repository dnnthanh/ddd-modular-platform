package dev.demo.core.web;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.demo.core.exception.ErrorCategory;
import java.util.Map;
import java.util.Objects;
import org.junit.jupiter.api.Test;

class ApiResponseTest {

  @Test
  void successAndErrorHaveNoTraceIdField() {
    assertTrue(ApiResponse.ok("ok").success());
    ApiResponse<Void> failure =
        ApiResponse.failure(
            new ApiFailure("NOT_FOUND", ErrorCategory.RESOURCE, "Missing", "/api/test", Map.of()));
    assertFalse(failure.success());
    assertTrue(Objects.nonNull(failure.timestamp()));
  }
}
