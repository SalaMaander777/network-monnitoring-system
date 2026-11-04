package ru.netmon.auth;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.security.web.csrf.CsrfException;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.stereotype.Component;
import ru.netmon.common.security.CurrentUser;
import tools.jackson.databind.json.JsonMapper;

/** JSON-ответы цепочки безопасности (architecture.md §5.1). Тело пишется байтами: getWriter() испортил бы русский текст. */
@Component
class JsonAuthHandlers implements AuthenticationEntryPoint, AccessDeniedHandler, AuthenticationSuccessHandler,
    AuthenticationFailureHandler {

  private final JsonMapper json;

  JsonAuthHandlers(JsonMapper json) {
    this.json = json;
  }

  private void write(HttpServletResponse response, int status, Object body) throws IOException {
    response.setStatus(status);
    response.setContentType("application/json");
    response.getOutputStream().write(json.writeValueAsBytes(body));
  }

  private void error(HttpServletResponse response, int status, String code, String message) throws IOException {
    Map<String, Object> body = new LinkedHashMap<>();
    body.put("code", code);
    body.put("message", message);
    body.put("fields", List.of());
    write(response, status, body);
  }

  @Override
  public void commence(HttpServletRequest request, HttpServletResponse response, AuthenticationException e)
      throws IOException {
    error(response, 401, "AUTH_REQUIRED", "Требуется вход в систему");
  }

  @Override
  public void handle(HttpServletRequest request, HttpServletResponse response, AccessDeniedException e)
      throws IOException {
    if (e instanceof CsrfException) {
      error(response, 403, "CSRF_INVALID", "Недействительный токен защиты, обновите страницу");
    } else {
      error(response, 403, "FORBIDDEN", "Недостаточно прав");
    }
  }

  @Override
  public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
      Authentication authentication) throws IOException {
    // Вход удалил прежний токен; getToken() заставляет записать новую cookie XSRF-TOKEN в этот ответ.
    CsrfToken token = (CsrfToken) request.getAttribute(CsrfToken.class.getName());
    if (token != null) {
      token.getToken();
    }
    CurrentUser user = (CurrentUser) authentication.getPrincipal();
    Map<String, Object> body = new LinkedHashMap<>();
    body.put("login", user.login());
    body.put("role", user.role().name());
    write(response, 200, body);
  }

  @Override
  public void onAuthenticationFailure(HttpServletRequest request, HttpServletResponse response,
      AuthenticationException e) throws IOException {
    error(response, 401, "AUTH_FAILED", "Неверный логин или пароль");
  }
}
