package com.staffdesk.common;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import java.util.*;
import org.slf4j.*;
import org.springframework.dao.*;
import org.springframework.http.*;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@org.springframework.core.annotation.Order(org.springframework.core.Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice
public class Errors {
  private static final Logger log = LoggerFactory.getLogger(Errors.class);

  @ExceptionHandler(ApiException.class)
  ResponseEntity<ProblemDetail> api(ApiException e, HttpServletRequest r) {
    return problem(e.status, e.getMessage(), r, null);
  }

  @ExceptionHandler(MethodArgumentNotValidException.class)
  ResponseEntity<ProblemDetail> validation(
      MethodArgumentNotValidException e, HttpServletRequest r) {
    Map<String, String> fields = new TreeMap<>();
    e.getBindingResult()
        .getFieldErrors()
        .forEach(f -> fields.putIfAbsent(f.getField(), f.getDefaultMessage()));
    return problem(HttpStatus.BAD_REQUEST, "Check the highlighted fields.", r, fields);
  }

  @ExceptionHandler({
    ConstraintViolationException.class,
    HandlerMethodValidationException.class,
    HttpMessageNotReadableException.class,
    MethodArgumentTypeMismatchException.class
  })
  ResponseEntity<ProblemDetail> invalid(Exception e, HttpServletRequest r) {
    return problem(HttpStatus.BAD_REQUEST, "Invalid request parameters or JSON.", r, null);
  }

  @ExceptionHandler({
    DataIntegrityViolationException.class,
    OptimisticLockingFailureException.class
  })
  ResponseEntity<ProblemDetail> conflict(Exception e, HttpServletRequest r) {
    return problem(
        HttpStatus.CONFLICT,
        "Email already exists or the record changed. Reload and try again.",
        r,
        null);
  }

  @ExceptionHandler(AccessDeniedException.class)
  ResponseEntity<ProblemDetail> forbidden(Exception e, HttpServletRequest r) {
    return problem(HttpStatus.FORBIDDEN, "This action is not permitted.", r, null);
  }

  @ExceptionHandler(org.springframework.web.ErrorResponseException.class)
  ResponseEntity<ProblemDetail> framework(
      org.springframework.web.ErrorResponseException e, HttpServletRequest r) {
    return problem(
        HttpStatus.valueOf(e.getStatusCode().value()),
        "The request could not be processed.",
        r,
        null);
  }

  @ExceptionHandler(org.springframework.web.bind.ServletRequestBindingException.class)
  ResponseEntity<ProblemDetail> binding(Exception e, HttpServletRequest r) {
    return problem(
        HttpStatus.BAD_REQUEST, "A required request parameter is missing or invalid.", r, null);
  }

  @ExceptionHandler(org.springframework.web.HttpRequestMethodNotSupportedException.class)
  ResponseEntity<ProblemDetail> method(Exception e, HttpServletRequest r) {
    return problem(HttpStatus.METHOD_NOT_ALLOWED, "HTTP method not supported.", r, null);
  }

  @ExceptionHandler(org.springframework.web.servlet.resource.NoResourceFoundException.class)
  ResponseEntity<ProblemDetail> notFound(Exception e, HttpServletRequest r) {
    return problem(HttpStatus.NOT_FOUND, "Resource not found.", r, null);
  }

  @ExceptionHandler(org.springframework.web.HttpMediaTypeNotSupportedException.class)
  ResponseEntity<ProblemDetail> media(Exception e, HttpServletRequest r) {
    return problem(
        HttpStatus.UNSUPPORTED_MEDIA_TYPE, "Use Content-Type: application/json.", r, null);
  }

  @ExceptionHandler(org.springframework.web.HttpMediaTypeNotAcceptableException.class)
  ResponseEntity<ProblemDetail> accept(Exception e, HttpServletRequest r) {
    return problem(
        HttpStatus.NOT_ACCEPTABLE, "Requested response format is not supported.", r, null);
  }

  @ExceptionHandler(Exception.class)
  ResponseEntity<ProblemDetail> unexpected(Exception e, HttpServletRequest r) {
    if (org.springframework.web.util.DisconnectedClientHelper.isClientDisconnectedException(e)) {
      log.debug("Client disconnected requestId={}", r.getAttribute("requestId"));
      return null; // Response may already be committed; never try writing a new error body.
    }
    log.error("Unhandled failure requestId={}", r.getAttribute("requestId"), e);
    return problem(
        HttpStatus.INTERNAL_SERVER_ERROR,
        "An unexpected error occurred. Contact support with the request ID.",
        r,
        null);
  }

  static ResponseEntity<ProblemDetail> problem(
      HttpStatus s, String detail, HttpServletRequest r, Map<String, String> fields) {
    ProblemDetail p = ProblemDetail.forStatusAndDetail(s, detail);
    p.setProperty("requestId", r.getAttribute("requestId"));
    if (fields != null) p.setProperty("fields", fields);
    return ResponseEntity.status(s).body(p);
  }
}
