package dev.demo.core.exception;

import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.demo.core.exception.type.NotFoundException;
import org.junit.jupiter.api.Test;

class AppExceptionTest {
  @Test
  void polymorphicCodeAndCategory() {
    AppException ex = new NotFoundException(ErrorCode.CATALOG_NOT_FOUND);
    assertEquals(FailureKind.NOT_FOUND, ex.getKind());
    assertEquals(ErrorCategory.RESOURCE, ex.getCategory());
    assertEquals(ErrorCode.CATALOG_NOT_FOUND, ex.getErrorCode());
  }
}
