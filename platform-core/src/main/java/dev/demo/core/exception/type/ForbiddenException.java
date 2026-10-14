package dev.demo.core.exception.type;

import dev.demo.core.exception.ErrorCode;

public class ForbiddenException extends DomainException {

  public ForbiddenException(ErrorCode code, Object... args) {
    super(code, args);
  }

  public ForbiddenException() {
    super(ErrorCode.FORBIDDEN);
  }
}
