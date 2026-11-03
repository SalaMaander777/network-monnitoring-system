package ru.netmon.common.web;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.validation.method.ParameterErrors;
import org.springframework.validation.method.ParameterValidationResult;
import org.springframework.web.context.request.async.AsyncRequestNotUsableException;
import org.springframework.web.context.request.async.AsyncRequestTimeoutException;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/**
 * Единый формат ошибок (architecture.md §5.1). В тело не попадают текст исключения, имена классов, SQL
 * и отвергнутые значения.
 */
@RestControllerAdvice
public class ApiErrorHandler {

  private static final Logger LOG = LoggerFactory.getLogger(ApiErrorHandler.class);

  private static final String VALIDATION_MESSAGE = "Проверьте введённые данные";

  private static ResponseEntity<Map<String, Object>> response(HttpStatusCode status, String code, String message,
      List<FieldError> fields, Map<String, Object> details) {
    Map<String, Object> body = new LinkedHashMap<>();
    body.put("code", code);
    body.put("message", message);
    body.put("fields", fields);
    body.putAll(details);
    return ResponseEntity.status(status).contentType(MediaType.APPLICATION_JSON).body(body);
  }

  private static ResponseEntity<Map<String, Object>> response(HttpStatusCode status, String code, String message,
      List<FieldError> fields) {
    return response(status, code, message, fields, Map.of());
  }

  @ExceptionHandler(ApiException.class)
  ResponseEntity<Map<String, Object>> apiException(ApiException e) {
    return response(e.status(), e.code(), e.getMessage(), e.fields(), e.details());
  }

  @ExceptionHandler(MethodArgumentNotValidException.class)
  ResponseEntity<Map<String, Object>> notValid(MethodArgumentNotValidException e) {
    List<FieldError> fields = new ArrayList<>();
    e.getBindingResult().getFieldErrors()
        .forEach(x -> fields.add(new FieldError(x.getField(), x.getDefaultMessage())));
    e.getBindingResult().getGlobalErrors()
        .forEach(x -> fields.add(new FieldError(null, x.getDefaultMessage())));
    return response(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", VALIDATION_MESSAGE, fields);
  }

  @ExceptionHandler(HandlerMethodValidationException.class)
  ResponseEntity<Map<String, Object>> methodValidation(HandlerMethodValidationException e) {
    List<FieldError> fields = new ArrayList<>();
    for (ParameterValidationResult r : e.getParameterValidationResults()) {
      if (r instanceof ParameterErrors pe) {
        pe.getFieldErrors().forEach(x -> fields.add(new FieldError(x.getField(), x.getDefaultMessage())));
        pe.getGlobalErrors().forEach(x -> fields.add(new FieldError(null, x.getDefaultMessage())));
      } else {
        String name = r.getMethodParameter().getParameterName();
        if (r.getContainerIndex() != null) {
          name += "[" + r.getContainerIndex() + "]";
        } else if (r.getContainerKey() != null) {
          name += "[" + r.getContainerKey() + "]";
        }
        String field = name;
        r.getResolvableErrors().forEach(x -> fields.add(new FieldError(field, x.getDefaultMessage())));
      }
    }
    return response(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", VALIDATION_MESSAGE, fields);
  }

  @ExceptionHandler(HttpMessageNotReadableException.class)
  ResponseEntity<Map<String, Object>> notReadable(HttpMessageNotReadableException e) {
    return response(HttpStatus.BAD_REQUEST, "BAD_JSON",
        "Тело запроса отсутствует или не является корректным JSON", List.of());
  }

  @ExceptionHandler(MethodArgumentTypeMismatchException.class)
  ResponseEntity<Map<String, Object>> typeMismatch(MethodArgumentTypeMismatchException e) {
    return response(HttpStatus.BAD_REQUEST, "BAD_PARAMETER", "Неверное значение параметра",
        List.of(new FieldError(e.getName(), "Неверное значение")));
  }

  @ExceptionHandler(DataIntegrityViolationException.class)
  ResponseEntity<Map<String, Object>> dataIntegrity(DataIntegrityViolationException e,
      HttpServletRequest request) {
    LOG.warn("Нарушение ограничения БД {} {}", request.getMethod(), request.getRequestURI());
    return response(HttpStatus.CONFLICT, "DATA_CONFLICT", "Данные противоречат существующим записям", List.of());
  }

  /** Код ошибки 4xx по постоянной таблице (architecture.md §5.1); общая функция для /error (T1.05). */
  static String codeFor(int status) {
    return switch (status) {
      case 400 -> "BAD_REQUEST";
      case 401 -> "UNAUTHORIZED";
      case 403 -> "FORBIDDEN";
      case 404 -> "NOT_FOUND";
      case 405 -> "METHOD_NOT_ALLOWED";
      case 406 -> "NOT_ACCEPTABLE";
      case 413 -> "PAYLOAD_TOO_LARGE";
      case 415 -> "UNSUPPORTED_MEDIA_TYPE";
      default -> "HTTP_" + status;
    };
  }

  private static final int MAX_CAUSES = 20;

  private static boolean inChain(Throwable e, Predicate<Throwable> test) {
    Set<Throwable> seen = Collections.newSetFromMap(new IdentityHashMap<>());
    Throwable t = e;
    for (int i = 0; t != null && i < MAX_CAUSES && seen.add(t); i++) {
      if (test.test(t)) {
        return true;
      }
      t = t.getCause();
    }
    return false;
  }

  /**
   * Исключения безопасности пробрасываются (ответ даёт фильтр безопасности); для начатого ответа JSON не
   * пишется.
   */
  @ExceptionHandler(Exception.class)
  ResponseEntity<Map<String, Object>> other(Exception e, HttpServletRequest request, HttpServletResponse response)
      throws Exception {
    if (inChain(e, t -> t instanceof AccessDeniedException || t instanceof AuthenticationException)) {
      throw e;
    }
    if (e instanceof AsyncRequestNotUsableException) {
      LOG.debug("Клиент ушёл {} {}", request.getMethod(), request.getRequestURI());
      return null;
    }
    if (response.isCommitted()) {
      if (e instanceof AsyncRequestTimeoutException || inChain(e, t -> t instanceof IOException)) {
        LOG.debug("Обрыв начатого ответа {} {}", request.getMethod(), request.getRequestURI());
      } else {
        LOG.error("Необработанная ошибка {} {}", request.getMethod(), request.getRequestURI(), e);
      }
      return null;
    }
    if (e instanceof AsyncRequestTimeoutException) {
      LOG.debug("Таймаут асинхронного запроса {} {}", request.getMethod(), request.getRequestURI());
      return response(HttpStatus.SERVICE_UNAVAILABLE, "SERVICE_UNAVAILABLE", "Сервис временно недоступен",
          List.of());
    }
    if (e instanceof ErrorResponse er && er.getStatusCode().is4xxClientError()) {
      return response(er.getStatusCode(), codeFor(er.getStatusCode().value()), "Запрос не может быть выполнен",
          List.of());
    }
    LOG.error("Необработанная ошибка {} {}", request.getMethod(), request.getRequestURI(), e);
    return response(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", "Внутренняя ошибка сервера", List.of());
  }
}
