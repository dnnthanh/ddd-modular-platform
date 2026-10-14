package dev.demo.core.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** Stable machine codes + i18n keys; never accept free-form messages from clients. */
@Getter
@RequiredArgsConstructor
public enum ErrorCode {
  BAD_REQUEST(FailureKind.BAD_REQUEST),
  VALIDATION_FAILED(FailureKind.BAD_REQUEST),
  MALFORMED_BODY(FailureKind.BAD_REQUEST),
  UNSUPPORTED_MEDIA_TYPE(FailureKind.BAD_REQUEST),
  METHOD_NOT_ALLOWED(FailureKind.BAD_REQUEST),
  NOT_ACCEPTABLE(FailureKind.BAD_REQUEST),
  PAYLOAD_TOO_LARGE(FailureKind.BAD_REQUEST),
  UNAUTHORIZED(FailureKind.UNAUTHORIZED),
  INVALID_CREDENTIALS(FailureKind.UNAUTHORIZED),
  TOKEN_REVOKED(FailureKind.UNAUTHORIZED),
  FORBIDDEN(FailureKind.FORBIDDEN),
  RESOURCE_NOT_FOUND(FailureKind.NOT_FOUND),
  CATALOG_NOT_FOUND(FailureKind.NOT_FOUND),
  USER_NOT_FOUND(FailureKind.NOT_FOUND),
  ROLE_NOT_FOUND(FailureKind.NOT_FOUND),
  CONFLICT(FailureKind.CONFLICT),
  DATA_CONFLICT(FailureKind.CONFLICT),
  DUPLICATE_USERNAME(FailureKind.CONFLICT),
  DUPLICATE_ROLE(FailureKind.CONFLICT),
  DUPLICATE_SKU(FailureKind.CONFLICT),
  INVALID_SKU(FailureKind.BUSINESS_RULE),
  INVALID_NAME(FailureKind.BUSINESS_RULE),
  INVALID_ROLE(FailureKind.BAD_REQUEST),
  INVALID_PERMISSION(FailureKind.BAD_REQUEST),
  INVALID_PAGINATION(FailureKind.BAD_REQUEST),
  PRECONDITION_FAILED(FailureKind.PRECONDITION_FAILED),
  RESOURCE_GONE(FailureKind.GONE),
  TOO_MANY_REQUESTS(FailureKind.TOO_MANY_REQUESTS),
  DATABASE_BUSY(FailureKind.SERVICE_UNAVAILABLE),
  DATA_UNAVAILABLE(FailureKind.SERVICE_UNAVAILABLE),
  SERVICE_UNAVAILABLE(FailureKind.SERVICE_UNAVAILABLE),
  REQUEST_TIMEOUT(FailureKind.GATEWAY_TIMEOUT),
  INTERNAL_ERROR(FailureKind.INTERNAL);

  private final FailureKind kind;

  public String messageKey() {
    return "error." + name();
  }
}
