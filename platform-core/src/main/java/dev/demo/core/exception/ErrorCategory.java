package dev.demo.core.exception;

public enum ErrorCategory {
  VALIDATION,
  AUTHENTICATION,
  AUTHORIZATION,
  RESOURCE,
  CONFLICT,
  BUSINESS,
  RATE_LIMIT,
  DEPENDENCY,
  INTERNAL
}
