package ru.netmon.settings;

import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import ru.netmon.common.security.CurrentUser;

@RestController
class SettingsController {
  private final SettingsService service;

  SettingsController(SettingsService service) { this.service = service; }

  @PreAuthorize("hasRole('ADMIN')")
  @GetMapping("/api/settings")
  List<SettingView> list() { return service.list(); }

  @PreAuthorize("hasRole('ADMIN')")
  @PutMapping("/api/settings/{key}")
  SettingView update(@PathVariable String key, @RequestBody SettingUpdate body, @AuthenticationPrincipal CurrentUser user) {
    return service.update(key, body.value(), user.actor());
  }
}
