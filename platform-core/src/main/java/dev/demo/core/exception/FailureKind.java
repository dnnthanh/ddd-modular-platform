package dev.demo.core.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum FailureKind {
  BAD_REQUEST(ErrorCategory.VALIDATION),
  UNPROCESSABLE(ErrorCategory.VALIDATION),
  UNAUTHORIZED(ErrorCategory.AUTHENTICATION),
  FORBIDDEN(ErrorCategory.AUTHORIZATION),
  NOT_FOUND(ErrorCategory.RESOURCE),
  GONE(ErrorCategory.RESOURCE),
  CONFLICT(ErrorCategory.CONFLICT),
  PRECONDITION_FAILED(ErrorCategory.CONFLICT),
  TOO_MANY_REQUESTS(ErrorCategory.RATE_LIMIT),
  SERVICE_UNAVAILABLE(ErrorCategory.DEPENDENCY),
  GATEWAY_TIMEOUT(ErrorCategory.DEPENDENCY),
  BUSINESS_RULE(ErrorCategory.BUSINESS),
  INTERNAL(ErrorCategory.INTERNAL);

  private final ErrorCategory category;
}
