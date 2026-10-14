package dev.demo.core.web;

import java.time.Instant;

/** Stable envelope; trace/span IDs belong in OpenTelemetry, not JSON responses. */
public record ApiResponse<T>(boolean success, T data, ApiFailure error, Instant timestamp) {

  public static <T> ApiResponse<T> ok(T value) {
    return new ApiResponse<>(true, value, null, Instant.now());
  }

  public static ApiResponse<Void> failure(ApiFailure error) {
    return new ApiResponse<>(false, null, error, Instant.now());
  }
}
