package ru.netmon.audit;

import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import ru.netmon.common.json.NulGuard;
import ru.netmon.common.json.SecretFieldGuard;
import ru.netmon.contract.Actor;
import ru.netmon.contract.AuditLog;
import ru.netmon.contract.EventJournal;
import ru.netmon.contract.EventType;
import tools.jackson.databind.json.JsonMapper;

@Service
class AuditLogService implements AuditLog {
  private static final Pattern ACTION = Pattern.compile("[A-Z][A-Z_]{0,63}");
  private static final Pattern OBJECT_TYPE = Pattern.compile("[a-z][a-z_]{0,63}");
  private final AuditRepository repository;
  private final JsonMapper json;
  private final Clock clock;
  private final EventJournal events;

  AuditLogService(AuditRepository repository, JsonMapper json, Clock clock, EventJournal events) {
    this.repository = repository; this.json = json; this.clock = clock; this.events = events;
  }

  private String toJson(Object v) {
    return v == null ? null : json.writeValueAsString(NulGuard.tree(SecretFieldGuard.strip(json.valueToTree(v))));
  }

  @Override
  @Transactional(propagation = Propagation.MANDATORY)
  public void record(Actor a, String action, String objectType, String objectId, Object oldValue, Object newValue) {
    Objects.requireNonNull(a, "a");
    if (action == null || !ACTION.matcher(action).matches()) throw new IllegalArgumentException("action");
    if (objectType == null || !OBJECT_TYPE.matcher(objectType).matches()) throw new IllegalArgumentException("objectType");
    Instant ts = clock.instant();
    long id = repository.insert(ts, a, action, objectType, NulGuard.text(objectId), toJson(oldValue), toJson(newValue));
    Map<String, Object> details = new LinkedHashMap<>();
    details.put("auditId", id);
    details.put("userId", a.userId());
    details.put("login", a.login());
    details.put("action", action);
    details.put("objectType", objectType);
    details.put("objectId", objectId);
    Long deviceId = null;
    if ("device".equals(objectType) && objectId != null && objectId.matches("[0-9]{1,18}")) deviceId = Long.valueOf(objectId);
    events.record(EventType.USER_ACTION, deviceId, null, "Действие пользователя " + a.login() + ": " + action + " "
        + objectType + (objectId == null ? "" : " " + objectId), details, ts);
  }
}
