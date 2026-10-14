package dev.demo.core.exception.type;

import dev.demo.core.exception.AppException;
import dev.demo.core.exception.ErrorCode;

public class DomainException extends AppException {

  public DomainException(ErrorCode code, Object... args) {
    super(code, args);
  }
}
