package ru.netmon.auth;

import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import ru.netmon.common.security.CurrentUser;

@RestController
class MeController {

  @PreAuthorize("hasAnyRole('OPERATOR','ADMIN')")
  @GetMapping("/api/me")
  Map<String, Object> me(@AuthenticationPrincipal CurrentUser user) {
    Map<String, Object> body = new LinkedHashMap<>();
    body.put("id", user.id());
    body.put("login", user.login());
    body.put("role", user.role().name());
    return body;
  }
}
