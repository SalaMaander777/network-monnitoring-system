package ru.netmon.audit;

import java.time.Instant;
import tools.jackson.databind.JsonNode;

/** Запись журнала действий в ответе GET /api/audit (architecture.md §5.2); порядок компонентов задаёт порядок полей JSON. */
public record AuditEntry(long id, Instant ts, Long userId, String userLogin, String action, String objectType,
    String objectId, JsonNode oldValue, JsonNode newValue) {}
