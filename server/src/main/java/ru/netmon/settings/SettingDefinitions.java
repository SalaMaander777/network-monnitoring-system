package ru.netmon.settings;

import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

final class SettingDefinitions {
  record Definition(String key, int min, int max) {
    private static final Pattern DIGITS = Pattern.compile("[1-9][0-9]{0,9}");
    String message() { return "Целое число от " + min + " до " + max; }
    Optional<String> violation(String value) {
      if (value == null || !DIGITS.matcher(value).matches()) return Optional.of(message());
      long v = Long.parseLong(value);
      return v < min || v > max ? Optional.of(message()) : Optional.empty();
    }
  }

  static final List<Definition> ALL = List.of(
      new Definition("auth.lock_attempts", 1, 100),
      new Definition("auth.lock_minutes", 1, 1440),
      new Definition("retention.journal_days", 1, 3650),
      new Definition("retention.results_days", 1, 3650),
      new Definition("session.timeout_minutes", 1, 43200),
      new Definition("traps.udp_port", 1, 65535));

  private SettingDefinitions() {}

  static Optional<Definition> find(String key) {
    return ALL.stream().filter(d -> d.key().equals(key)).findFirst();
  }
}
