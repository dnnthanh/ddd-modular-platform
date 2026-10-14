package dev.demo.core.exception.type;

import dev.demo.core.exception.ErrorCode;

public class NotFoundException extends DomainException {

  public NotFoundException(ErrorCode code, Object... args) {
    super(code, args);
  }

  public NotFoundException() {
    super(ErrorCode.RESOURCE_NOT_FOUND);
  }
}
