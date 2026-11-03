package ru.netmon.common.web;

import java.util.List;

/** Постраничный ответ (architecture.md §5.1); порядок полей JSON равен порядку компонентов. */
public record PageResponse<T>(List<T> items, int page, int size, long total) {

  public static <T> PageResponse<T> of(List<T> items, PageQuery q, long total) {
    return new PageResponse<>(List.copyOf(items), q.page(), q.size(), total);
  }
}
