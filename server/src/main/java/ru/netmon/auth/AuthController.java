package ru.netmon.auth;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
class AuthController {

  /** Выдаёт cookie XSRF-TOKEN: вызов getToken() заставляет отложенный токен записаться в ответ. */
  @PreAuthorize("permitAll()")
  @GetMapping("/api/auth/csrf")
  ResponseEntity<Void> csrf(CsrfToken token) {
    token.getToken();
    return ResponseEntity.noContent().build();
  }
}
