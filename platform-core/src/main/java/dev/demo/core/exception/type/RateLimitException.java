package dev.demo.core.exception.type;

import dev.demo.core.exception.ErrorCode;

public class RateLimitException extends DomainException {

  public RateLimitException(ErrorCode code, Object... args) {
    super(code, args);
  }

  public RateLimitException() {
    super(ErrorCode.TOO_MANY_REQUESTS);
  }
}
