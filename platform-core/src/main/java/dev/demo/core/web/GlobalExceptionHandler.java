package dev.demo.core.web;

import dev.demo.core.exception.AppException;
import dev.demo.core.exception.ErrorCode;
import dev.demo.core.exception.FailureKind;
import dev.demo.core.i18n.MessageResolver;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.HttpMediaTypeNotAcceptableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.async.AsyncRequestTimeoutException;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@Slf4j
@RestControllerAdvice
@RequiredArgsConstructor
public class GlobalExceptionHandler {

  private final MessageResolver messages;

  private String localize(ErrorCode code, Object... args) {
    return messages.resolve(code, args);
  }

  /** Log every handled error with a throwable; redact framework client-input exceptions. */
  private ResponseEntity<ApiResponse<Void>> error(
      HttpStatus status,
      ErrorCode code,
      HttpServletRequest request,
      Exception exception,
      Map<String, String> details,
      Object... args) {
    log.error(
        "api.exception status={} code={} method={} path={} type={}",
        status.value(),
        code.name(),
        request.getMethod(),
        request.getRequestURI(),
        exception.getClass().getSimpleName(),
        loggingException(status, exception));
    var failure =
        new ApiFailure(
            code.name(),
            code.getKind().getCategory(),
            localize(code, args),
            request.getRequestURI(),
            details);
    return ResponseEntity.status(status).body(ApiResponse.failure(failure));
  }

  private Throwable loggingException(HttpStatus status, Exception exception) {
    if (!status.is4xxClientError() || exception instanceof AppException) {
      return exception;
    }
    // Framework binding/JSON exceptions can embed rejected passwords and token values.
    // Preserve the original stack location, but do not copy its message, cause or suppressed
    // errors.
    var sanitized = new Exception(exception.getClass().getName() + ": request input redacted");
    sanitized.setStackTrace(exception.getStackTrace());
    return sanitized;
  }

  private HttpStatus status(FailureKind kind) {
    return switch (kind) {
      case BAD_REQUEST -> HttpStatus.BAD_REQUEST;
      case UNPROCESSABLE, BUSINESS_RULE -> HttpStatus.UNPROCESSABLE_CONTENT;
      case UNAUTHORIZED -> HttpStatus.UNAUTHORIZED;
      case FORBIDDEN -> HttpStatus.FORBIDDEN;
      case NOT_FOUND -> HttpStatus.NOT_FOUND;
      case GONE -> HttpStatus.GONE;
      case CONFLICT -> HttpStatus.CONFLICT;
      case PRECONDITION_FAILED -> HttpStatus.PRECONDITION_FAILED;
      case TOO_MANY_REQUESTS -> HttpStatus.TOO_MANY_REQUESTS;
      case SERVICE_UNAVAILABLE -> HttpStatus.SERVICE_UNAVAILABLE;
      case GATEWAY_TIMEOUT -> HttpStatus.GATEWAY_TIMEOUT;
      case INTERNAL -> HttpStatus.INTERNAL_SERVER_ERROR;
    };
  }

  @ExceptionHandler(AppException.class)
  ResponseEntity<ApiResponse<Void>> application(AppException ex, HttpServletRequest request) {
    return error(status(ex.getKind()), ex.getErrorCode(), request, ex, Map.of(), ex.getArguments());
  }

  @ExceptionHandler(MethodArgumentNotValidException.class)
  ResponseEntity<ApiResponse<Void>> validation(
      MethodArgumentNotValidException ex, HttpServletRequest request) {
    Map<String, String> fields = new LinkedHashMap<>();
    ex.getBindingResult()
        .getFieldErrors()
        .forEach(
            field ->
                fields.putIfAbsent(
                    field.getField(),
                    Objects.toString(field.getDefaultMessage(), "Invalid value")));
    return error(HttpStatus.BAD_REQUEST, ErrorCode.VALIDATION_FAILED, request, ex, fields);
  }

  @ExceptionHandler(ConstraintViolationException.class)
  ResponseEntity<ApiResponse<Void>> constraint(
      ConstraintViolationException ex, HttpServletRequest request) {
    Map<String, String> fields = new LinkedHashMap<>();
    ex.getConstraintViolations()
        .forEach(
            violation ->
                fields.putIfAbsent(violation.getPropertyPath().toString(), violation.getMessage()));
    return error(HttpStatus.BAD_REQUEST, ErrorCode.VALIDATION_FAILED, request, ex, fields);
  }

  @ExceptionHandler(HandlerMethodValidationException.class)
  ResponseEntity<ApiResponse<Void>> methodValidation(
      HandlerMethodValidationException ex, HttpServletRequest request) {
    Map<String, String> fields = new LinkedHashMap<>();
    ex.getParameterValidationResults()
        .forEach(
            result -> {
              String parameter = result.getMethodParameter().getParameterName();
              String fieldName =
                  Objects.nonNull(parameter)
                      ? parameter
                      : "arg" + result.getMethodParameter().getParameterIndex();
              result
                  .getResolvableErrors()
                  .forEach(
                      message ->
                          fields.putIfAbsent(
                              fieldName,
                              Objects.toString(message.getDefaultMessage(), "Invalid value")));
            });
    return error(HttpStatus.BAD_REQUEST, ErrorCode.VALIDATION_FAILED, request, ex, fields);
  }

  @ExceptionHandler({
    HttpMessageNotReadableException.class,
    MethodArgumentTypeMismatchException.class,
    MissingServletRequestParameterException.class,
    MissingRequestHeaderException.class
  })
  ResponseEntity<ApiResponse<Void>> malformed(Exception ex, HttpServletRequest request) {
    Map<String, String> fields = new LinkedHashMap<>();
    if (ex instanceof MethodArgumentTypeMismatchException mismatch) {
      fields.put(mismatch.getName(), "Invalid value or type");
    } else if (ex instanceof MissingServletRequestParameterException missing) {
      fields.put(missing.getParameterName(), "Required parameter is missing");
    } else if (ex instanceof MissingRequestHeaderException missing) {
      fields.put(missing.getHeaderName(), "Required header is missing");
    }
    return error(HttpStatus.BAD_REQUEST, ErrorCode.MALFORMED_BODY, request, ex, fields);
  }

  @ExceptionHandler({NoHandlerFoundException.class, NoResourceFoundException.class})
  ResponseEntity<ApiResponse<Void>> noRoute(Exception ex, HttpServletRequest request) {
    return error(HttpStatus.NOT_FOUND, ErrorCode.RESOURCE_NOT_FOUND, request, ex, Map.of());
  }

  @ExceptionHandler(MaxUploadSizeExceededException.class)
  ResponseEntity<ApiResponse<Void>> tooLarge(Exception ex, HttpServletRequest request) {
    return error(HttpStatus.CONTENT_TOO_LARGE, ErrorCode.PAYLOAD_TOO_LARGE, request, ex, Map.of());
  }

  @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
  ResponseEntity<ApiResponse<Void>> method(Exception ex, HttpServletRequest request) {
    return error(
        HttpStatus.METHOD_NOT_ALLOWED, ErrorCode.METHOD_NOT_ALLOWED, request, ex, Map.of());
  }

  @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
  ResponseEntity<ApiResponse<Void>> media(Exception ex, HttpServletRequest request) {
    return error(
        HttpStatus.UNSUPPORTED_MEDIA_TYPE, ErrorCode.UNSUPPORTED_MEDIA_TYPE, request, ex, Map.of());
  }

  @ExceptionHandler(HttpMediaTypeNotAcceptableException.class)
  ResponseEntity<ApiResponse<Void>> acceptable(Exception ex, HttpServletRequest request) {
    return error(HttpStatus.NOT_ACCEPTABLE, ErrorCode.NOT_ACCEPTABLE, request, ex, Map.of());
  }

  @ExceptionHandler(AccessDeniedException.class)
  ResponseEntity<ApiResponse<Void>> denied(Exception ex, HttpServletRequest request) {
    return error(HttpStatus.FORBIDDEN, ErrorCode.FORBIDDEN, request, ex, Map.of());
  }

  @ExceptionHandler(AuthenticationException.class)
  ResponseEntity<ApiResponse<Void>> unauthorized(Exception ex, HttpServletRequest request) {
    return error(HttpStatus.UNAUTHORIZED, ErrorCode.UNAUTHORIZED, request, ex, Map.of());
  }

  @ExceptionHandler({
    DataIntegrityViolationException.class,
    OptimisticLockingFailureException.class
  })
  ResponseEntity<ApiResponse<Void>> conflict(Exception ex, HttpServletRequest request) {
    return error(HttpStatus.CONFLICT, ErrorCode.DATA_CONFLICT, request, ex, Map.of());
  }

  @ExceptionHandler({QueryTimeoutException.class, CannotAcquireLockException.class})
  ResponseEntity<ApiResponse<Void>> busy(Exception ex, HttpServletRequest request) {
    return error(HttpStatus.SERVICE_UNAVAILABLE, ErrorCode.DATABASE_BUSY, request, ex, Map.of());
  }

  @ExceptionHandler(DataAccessException.class)
  ResponseEntity<ApiResponse<Void>> database(Exception ex, HttpServletRequest request) {
    return error(HttpStatus.SERVICE_UNAVAILABLE, ErrorCode.DATA_UNAVAILABLE, request, ex, Map.of());
  }

  @ExceptionHandler(AsyncRequestTimeoutException.class)
  ResponseEntity<ApiResponse<Void>> timeout(Exception ex, HttpServletRequest request) {
    return error(HttpStatus.GATEWAY_TIMEOUT, ErrorCode.REQUEST_TIMEOUT, request, ex, Map.of());
  }

  @ExceptionHandler(ResponseStatusException.class)
  ResponseEntity<ApiResponse<Void>> responseStatus(
      ResponseStatusException ex, HttpServletRequest request) {
    var status = HttpStatus.resolve(ex.getStatusCode().value());
    if (Objects.isNull(status) || status.is5xxServerError()) {
      return error(
          HttpStatus.INTERNAL_SERVER_ERROR, ErrorCode.INTERNAL_ERROR, request, ex, Map.of());
    }
    return error(status, ErrorCode.BAD_REQUEST, request, ex, Map.of());
  }

  @ExceptionHandler(Exception.class)
  ResponseEntity<ApiResponse<Void>> unexpected(Exception ex, HttpServletRequest request) {
    return error(HttpStatus.INTERNAL_SERVER_ERROR, ErrorCode.INTERNAL_ERROR, request, ex, Map.of());
  }
}
