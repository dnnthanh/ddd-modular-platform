package dev.demo.core.exception;

import java.util.Objects;
import lombok.Getter;

/** Shared exception contract; codes and categories are independent of Spring MVC. */
@Getter
public abstract class AppException extends RuntimeException {

  private final ErrorCode errorCode;
  private final Object[] arguments;

  protected AppException(ErrorCode errorCode, Object... arguments) {
    super(Objects.requireNonNull(errorCode).name());
    this.errorCode = errorCode;
    this.arguments = Objects.isNull(arguments) ? new Object[0] : arguments.clone();
  }

  /** Do not expose the mutable arguments array to callers. */
  public Object[] getArguments() {
    return arguments.clone();
  }

  public FailureKind getKind() {
    return errorCode.getKind();
  }

  public ErrorCategory getCategory() {
    return getKind().getCategory();
  }
}
