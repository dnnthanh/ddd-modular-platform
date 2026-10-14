package dev.demo.core.exception.type;

import dev.demo.core.exception.ErrorCode;

public class UnauthorizedException extends DomainException {

  public UnauthorizedException(ErrorCode code, Object... args) {
    super(code, args);
  }

  public UnauthorizedException() {
    super(ErrorCode.UNAUTHORIZED);
  }
}
