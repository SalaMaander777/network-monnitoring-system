package ru.netmon.common.web;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

public final class PeriodParams {
  public static final Instant MIN = LocalDate.of(1, 1, 1).atStartOfDay(ZoneOffset.UTC).toInstant();
  public static final Instant MAX_EXCLUSIVE = LocalDate.of(10000, 1, 1).atStartOfDay(ZoneOffset.UTC).toInstant();
  private PeriodParams() {}

  public static void check(Instant from, Instant to) {
    List<FieldError> errors = new ArrayList<>();
    if (out(from)) errors.add(new FieldError("from", "Дата вне диапазона 0001-01-01 .. 9999-12-31"));
    if (out(to)) errors.add(new FieldError("to", "Дата вне диапазона 0001-01-01 .. 9999-12-31"));
    if (!errors.isEmpty()) throw ApiException.validation(errors);
    if (from != null && to != null && from.isAfter(to)) {
      throw ApiException.validation(List.of(new FieldError(null, "Начало периода позже конца")));
    }
  }

  private static boolean out(Instant t) {
    return t != null && (t.isBefore(MIN) || !t.isBefore(MAX_EXCLUSIVE));
  }
}
