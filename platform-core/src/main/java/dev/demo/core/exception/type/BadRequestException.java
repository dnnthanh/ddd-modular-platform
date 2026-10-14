package dev.demo.core.exception.type;

import dev.demo.core.exception.ErrorCode;

public class BadRequestException extends DomainException {

  public BadRequestException(ErrorCode code, Object... args) {
    super(code, args);
  }

  public BadRequestException() {
    super(ErrorCode.BAD_REQUEST);
  }
}
