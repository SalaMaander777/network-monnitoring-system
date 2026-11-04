package ru.netmon.common.web;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.webmvc.error.ErrorController;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Страница ошибки контейнера (architecture.md §5.1): ошибки вне DispatcherServlet (исключение фильтра,
 * sendError) в формате API. Без @PreAuthorize (§2.2 п. 3): доступ задаёт dispatcherTypeMatchers(ERROR).
 */
@RestController
public class ApiErrorController implements ErrorController {

  private static final Logger LOG = LoggerFactory.getLogger(ApiErrorController.class);

  @RequestMapping("/error")
  public ResponseEntity<Map<String, Object>> error(HttpServletRequest request, HttpServletResponse response) {
    if (response.isCommitted()) {
      return null;
    }
    Object attr = request.getAttribute("jakarta.servlet.error.status_code");
    int status = attr instanceof Integer i ? i : 500;
    Map<String, Object> body = new LinkedHashMap<>();
    if (status >= 400 && status < 500) {
      body.put("code", ApiErrorHandler.codeFor(status));
      body.put("message", "Запрос не может быть выполнен");
    } else {
      status = 500;
      Object ex = request.getAttribute("jakarta.servlet.error.exception");
      if (ex instanceof Throwable t) {
        LOG.error("Необработанная ошибка {} {}", request.getMethod(),
            request.getAttribute("jakarta.servlet.error.request_uri"), t);
      }
      body.put("code", "INTERNAL_ERROR");
      body.put("message", "Внутренняя ошибка сервера");
    }
    body.put("fields", List.of());
    return ResponseEntity.status(status).contentType(MediaType.APPLICATION_JSON).body(body);
  }
}
