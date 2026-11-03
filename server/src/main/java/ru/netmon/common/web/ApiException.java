package ru.netmon.common.web;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.http.HttpStatus;

/** Ошибка API с кодом, сообщением и полями (architecture.md §5.1). Стек не заполняется: в лог она не пишется. */
public class ApiException extends RuntimeException {

  private static final Set<String> RESERVED = Set.of("code", "message", "fields");

  private final HttpStatus status;
  private final String code;
  private final List<FieldError> fields;
  private final Map<String, Object> details;

  public ApiException(HttpStatus status, String code, String message) {
    this(status, code, message, List.of(), Map.of());
  }

  public ApiException(HttpStatus status, String code, String message, List<FieldError> fields) {
    this(status, code, message, fields, Map.of());
  }

  public ApiException(HttpStatus status, String code, String message, List<FieldError> fields,
      Map<String, ?> details) {
    super(message, null, false, false);
    for (String key : details.keySet()) {
      if (RESERVED.contains(key)) {
        throw new IllegalArgumentException("Ключ details зарезервирован: " + key);
      }
    }
    this.status = status;
    this.code = code;
    this.fields = List.copyOf(fields);
    this.details = Collections.unmodifiableMap(new LinkedHashMap<String, Object>(details));
  }

  public static ApiException notFound() {
    return new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "Объект не найден");
  }

  public static ApiException versionConflict() {
    return new ApiException(HttpStatus.CONFLICT, "VERSION_CONFLICT",
        "Данные изменены другим пользователем, обновите страницу");
  }

  public static ApiException validation(List<FieldError> fields) {
    return new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "Проверьте введённые данные", fields);
  }

  public HttpStatus status() { return status; }

  public String code() { return code; }

  public List<FieldError> fields() { return fields; }

  public Map<String, Object> details() { return details; }
}
