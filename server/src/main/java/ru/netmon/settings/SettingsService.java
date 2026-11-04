package ru.netmon.settings;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.netmon.common.web.ApiException;
import ru.netmon.common.web.FieldError;
import ru.netmon.contract.Actor;
import ru.netmon.contract.AuditLog;
import ru.netmon.contract.Settings;
import ru.netmon.contract.events.SettingChanged;

@Service
class SettingsService implements Settings {
  private final JdbcClient jdbc;
  private final Clock clock;
  private final AuditLog audit;
  private final ApplicationEventPublisher events;

  SettingsService(JdbcClient jdbc, Clock clock, AuditLog audit, ApplicationEventPublisher events) {
    this.jdbc = jdbc; this.clock = clock; this.audit = audit; this.events = events;
  }

  private static SettingDefinitions.Definition def(String key) {
    return SettingDefinitions.find(key).orElseThrow(() -> new IllegalArgumentException("Неизвестная настройка: " + key));
  }

  @Override
  public String get(String key) {
    def(key);
    return jdbc.sql("select value from system_setting where key = ?").param(key).query(String.class).optional()
        .orElseThrow(() -> new IllegalStateException("Нет строки system_setting для ключа " + key));
  }

  @Override
  public int getInt(String key) {
    String v = get(key);
    if (def(key).violation(v).isPresent()) throw new IllegalStateException("Недопустимое значение настройки " + key + " в БД");
    return Integer.parseInt(v);
  }

  List<SettingView> list() {
    Map<String, String> rows = new HashMap<>();
    jdbc.sql("select key, value from system_setting").query(rs -> { rows.put(rs.getString(1), rs.getString(2)); });
    List<SettingView> out = new ArrayList<>();
    for (SettingDefinitions.Definition d : SettingDefinitions.ALL) {
      String v = rows.get(d.key());
      if (v == null) throw new IllegalStateException("Нет строки system_setting для ключа " + d.key());
      out.add(new SettingView(d.key(), v, d.min(), d.max()));
    }
    return out;
  }

  @Transactional
  SettingView update(String key, String value, Actor actor) {
    SettingDefinitions.Definition d = SettingDefinitions.find(key).orElseThrow(ApiException::notFound);
    Optional<String> bad = d.violation(value);
    if (bad.isPresent()) throw ApiException.validation(List.of(new FieldError("value", bad.get())));
    String old = jdbc.sql("select value from system_setting where key = ? for update").param(key).query(String.class)
        .optional().orElseThrow(() -> new IllegalStateException("Нет строки system_setting для ключа " + key));
    if (!old.equals(value)) {
      jdbc.sql("update system_setting set value = ?, updated_at = ?, updated_by = ? where key = ?")
          .params(value, OffsetDateTime.ofInstant(clock.instant(), ZoneOffset.UTC), actor.userId(), key).update();
      audit.record(actor, "UPDATE", "setting", key, Map.of("value", old), Map.of("value", value));
      events.publishEvent(new SettingChanged(key, value));
    }
    return new SettingView(key, value, d.min(), d.max());
  }
}
