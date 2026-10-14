package dev.demo.core.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import dev.demo.core.exception.ErrorCode;
import dev.demo.core.exception.type.NotFoundException;
import dev.demo.core.i18n.MessageResolver;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Validation;
import jakarta.validation.constraints.NotBlank;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.slf4j.LoggerFactory;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;

class GlobalExceptionHandlerTest {

  record ExampleRequest(@NotBlank String email) {}

  @Test
  void rejectedPasswordNeverAppearsInValidationLog() throws Exception {
    String secret = "private-password-probe";
    var binding = new BeanPropertyBindingResult(new Object(), "request");
    binding.addError(
        new FieldError("request", "password", secret, false, null, null, "Invalid password"));
    var method = GlobalExceptionHandlerTest.class.getDeclaredMethod("request", String.class);
    var exception = new MethodArgumentNotValidException(new MethodParameter(method, 0), binding);
    var logger = (Logger) LoggerFactory.getLogger(GlobalExceptionHandler.class);
    var logs = new ListAppender<ILoggingEvent>();
    logs.start();
    logger.addAppender(logs);
    try {
      var response =
          new GlobalExceptionHandler(Mockito.mock(MessageResolver.class))
              .validation(exception, new MockHttpServletRequest("POST", "/api/private/users"));
      assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
      assertEquals(1, logs.list.size());
      assertEquals(Level.ERROR, logs.list.getFirst().getLevel());
      assertNotNull(logs.list.getFirst().getThrowableProxy());
      assertFalse(
          ch.qos.logback.classic.spi.ThrowableProxyUtil.asString(
                  logs.list.getFirst().getThrowableProxy())
              .contains(secret));
      assertFalse(logs.list.getFirst().getFormattedMessage().contains(secret));
    } finally {
      logger.detachAppender(logs);
      logs.stop();
    }
  }

  private void request(String password) {}

  @Test
  void validationResponseContainsFieldNames() {
    try (var factory = Validation.buildDefaultValidatorFactory()) {
      var violations = factory.getValidator().validate(new ExampleRequest(""));
      var exception = new ConstraintViolationException(violations);
      var handler = new GlobalExceptionHandler(Mockito.mock(MessageResolver.class));
      var request = new MockHttpServletRequest("POST", "/api/private/users");
      var response = handler.constraint(exception, request);
      assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
      assertNotNull(response.getBody());
      assertTrue(response.getBody().error().details().containsKey("email"));
    }
  }

  @Test
  void applicationExceptionIsLoggedAtErrorWithThrowableAndCode() {
    var resolver = Mockito.mock(MessageResolver.class);
    var handler = new GlobalExceptionHandler(resolver);
    var request = new MockHttpServletRequest("GET", "/api/private/catalog/123");
    var logger = (Logger) LoggerFactory.getLogger(GlobalExceptionHandler.class);
    var logs = new ListAppender<ILoggingEvent>();
    logs.start();
    logger.addAppender(logs);
    try {
      var response =
          handler.application(new NotFoundException(ErrorCode.CATALOG_NOT_FOUND), request);
      assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
      assertFalse(logs.list.isEmpty());
      var last = logs.list.getLast();
      assertEquals(Level.ERROR, last.getLevel());
      assertNotNull(last.getThrowableProxy());
      assertTrue(last.getFormattedMessage().contains("CATALOG_NOT_FOUND"));
    } finally {
      logger.detachAppender(logs);
      logs.stop();
    }
  }
}
