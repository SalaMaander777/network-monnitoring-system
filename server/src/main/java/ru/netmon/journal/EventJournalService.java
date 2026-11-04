package ru.netmon.journal;

import java.net.InetAddress;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Objects;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import ru.netmon.common.json.NulGuard;
import ru.netmon.common.json.SecretFieldGuard;
import ru.netmon.contract.EventJournal;
import ru.netmon.contract.EventType;
import tools.jackson.databind.json.JsonMapper;

@Service
class EventJournalService implements EventJournal {
  private final JdbcClient jdbc;
  private final JsonMapper json;

  EventJournalService(JdbcClient jdbc, JsonMapper json) {
    this.jdbc = jdbc;
    this.json = json;
  }

  @Override
  @Transactional(propagation = Propagation.MANDATORY)
  public void record(EventType t, Long deviceId, InetAddress sourceIp, String description, Map<String, Object> details,
      Instant ts) {
    Objects.requireNonNull(t, "t");
    Objects.requireNonNull(description, "description");
    Objects.requireNonNull(ts, "ts");
    String d = details == null ? null : json.writeValueAsString(NulGuard.tree(SecretFieldGuard.strip(json.valueToTree(details))));
    jdbc.sql("insert into event(ts, device_id, type, source_ip, description, details) values (?, ?, ?, ?::inet, ?, ?::jsonb)")
        .params(OffsetDateTime.ofInstant(ts, ZoneOffset.UTC), deviceId, t.name(),
            host(sourceIp), NulGuard.text(description), d)
        .update();
  }

  private static String host(InetAddress ip) {
    if (ip == null) return null;
    String h = ip.getHostAddress();
    int p = h.indexOf('%');
    return p < 0 ? h : h.substring(0, p);
  }
}
