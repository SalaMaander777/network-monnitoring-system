package ru.netmon.common.web;

/** Ошибка поля запроса (architecture.md §5.1). {@code field} равен null для ошибки уровня объекта. */
public record FieldError(String field, String message) {}
