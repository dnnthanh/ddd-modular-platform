package dev.demo.core.exception.type;

import dev.demo.core.exception.ErrorCode;

public class DependencyException extends DomainException {

  public DependencyException(ErrorCode code, Object... args) {
    super(code, args);
  }

  public DependencyException() {
    super(ErrorCode.SERVICE_UNAVAILABLE);
  }
}
