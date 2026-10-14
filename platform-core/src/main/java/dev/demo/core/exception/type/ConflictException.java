package dev.demo.core.exception.type;

import dev.demo.core.exception.ErrorCode;

public class ConflictException extends DomainException {

  public ConflictException(ErrorCode code, Object... args) {
    super(code, args);
  }

  public ConflictException() {
    super(ErrorCode.CONFLICT);
  }
}
