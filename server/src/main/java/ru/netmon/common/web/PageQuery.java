package ru.netmon.common.web;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import org.springframework.http.HttpStatus;

/** Параметры страницы и сортировки (architecture.md §5.1, строгий разбор). */
public final class PageQuery {

  private static final Pattern DIGITS = Pattern.compile("[0-9]{1,10}");

  private final int page;
  private final int size;
  private final String sortColumn;
  private final boolean descending;

  private PageQuery(int page, int size, String sortColumn, boolean descending) {
    this.page = page;
    this.size = size;
    this.sortColumn = sortColumn;
    this.descending = descending;
  }

  public int page() { return page; }

  public int size() { return size; }

  public String sortColumn() { return sortColumn; }

  public boolean descending() { return descending; }

  public long offset() { return (long) page * size; }

  /**
   * Фрагмент ORDER BY: столбец сортировки и уникальный столбец {@code tieBreaker} (константа кода)
   * для стабильного порядка; совпадающий со столбцом сортировки не повторяется.
   */
  public String orderBy(String tieBreaker) {
    String dir = descending ? " desc" : " asc";
    String main = sortColumn + dir;
    return sortColumn.equals(tieBreaker) ? main : main + ", " + tieBreaker + dir;
  }

  /** @param allowed имя в API, выражение SQL: константы кода (белый список). */
  public static PageQuery parse(String page, String size, String sort, Map<String, String> allowed,
      String defaultSort) {
    String[] def = parseSort(defaultSort, allowed);
    if (def == null) {
      throw new IllegalArgumentException("Неверная сортировка по умолчанию");
    }
    List<FieldError> errors = new ArrayList<>();
    int p = parseInt(page, 0, Integer.MAX_VALUE, 0);
    if (p < 0) {
      errors.add(new FieldError("page", "Номер страницы: целое число от 0"));
    }
    int s = parseInt(size, 1, 500, 50);
    if (s < 0) {
      errors.add(new FieldError("size", "Размер страницы: целое число от 1 до 500"));
    }
    String[] so = sort == null ? def : parseSort(sort, allowed);
    if (so == null) {
      errors.add(new FieldError("sort", "Недопустимая сортировка"));
    }
    if (!errors.isEmpty()) {
      throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "Проверьте параметры запроса", errors);
    }
    return new PageQuery(p, s, so[0], "desc".equals(so[1]));
  }

  /** Возвращает -1 при нарушении (допустимые значения неотрицательны). */
  private static int parseInt(String v, int min, int max, int def) {
    if (v == null) {
      return def;
    }
    if (!DIGITS.matcher(v).matches()) {
      return -1;
    }
    long n = Long.parseLong(v);
    return n < min || n > max ? -1 : (int) n;
  }

  private static String[] parseSort(String v, Map<String, String> allowed) {
    String[] parts = v.split(",", -1);
    if (parts.length > 2) {
      return null;
    }
    String column = allowed.get(parts[0]);
    if (column == null) {
      return null;
    }
    String dir = parts.length == 2 ? parts[1] : "asc";
    if (!dir.equals("asc") && !dir.equals("desc")) {
      return null;
    }
    return new String[] {column, dir};
  }
}
