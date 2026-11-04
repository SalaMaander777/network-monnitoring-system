package ru.netmon.journal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.InetAddress;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import ru.netmon.contract.EventJournal;
import ru.netmon.contract.EventType;
import ru.netmon.testsupport.IntegrationTest;
import ru.netmon.testsupport.TestData;

@IntegrationTest
class EventJournalWriteTest {

  private static final Instant TS = Instant.parse("2026-01-01T00:00:00.123456Z");

  @Autowired
  EventJournal journal;

  @Autowired
  JdbcClient jdbc;

  @Autowired
  PlatformTransactionManager tm;

  @Autowired
  TestData data;

  private TransactionTemplate tx() {
    return new TransactionTemplate(tm);
  }

  private long events() {
    return jdbc.sql("select count(*) from event").query(Long.class).single();
  }

  @Test
  void recordsAllColumns() throws Exception {
    long dev = data.device(data.type(), data.profile());
    Map<String, Object> details = new HashMap<>();
    details.put("k", "v");
    details.put("n", null);
    tx().executeWithoutResult(s -> {
      try {
        journal.record(EventType.TRAP, dev, InetAddress.getByAddress(new byte[] {10, 1, 2, 3}), "описание", details,
            TS);
      } catch (java.net.UnknownHostException e) {
        throw new IllegalStateException(e);
      }
    });
    var row = jdbc.sql("select ts, device_id, type, host(source_ip) h, description,"
        + " details = '{\"k\":\"v\",\"n\":null}'::jsonb as d from event").query().singleRow();
    assertThat(((java.sql.Timestamp) row.get("ts")).toInstant()).isEqualTo(TS);
    assertThat(row.get("device_id")).isEqualTo(dev);
    assertThat(row.get("type")).isEqualTo("TRAP");
    assertThat(row.get("h")).isEqualTo("10.1.2.3");
    assertThat(row.get("description")).isEqualTo("описание");
    assertThat(row.get("d")).isEqualTo(true);
  }

  @Test
  void unknownDeviceAllowed() {
    tx().executeWithoutResult(s -> journal.record(EventType.TRAP_UNKNOWN_SOURCE, null, null, "x", null, TS));
    var row = jdbc.sql("select device_id is null a, source_ip is null b, details is null c from event").query()
        .singleRow();
    assertThat(row.get("a")).isEqualTo(true);
    assertThat(row.get("b")).isEqualTo(true);
    assertThat(row.get("c")).isEqualTo(true);
  }

  @Test
  void detailsStripped() {
    String secret = "UNIQ-" + java.util.UUID.randomUUID();
    tx().executeWithoutResult(s -> journal.record(EventType.TRAP, null, null, "x",
        Map.of("in", Map.of("community", secret, "ok", 1)), TS));
    assertThat(events()).isEqualTo(1);
    assertThat(jdbc.sql("select count(*) from event t where position(? in t::text) > 0").param(secret)
        .query(Long.class).single()).isZero();
  }

  @Test
  void requiresTransaction() {
    assertThatThrownBy(() -> journal.record(EventType.TRAP, null, null, "x", null, TS))
        .isInstanceOf(IllegalTransactionStateException.class);
    assertThat(events()).isZero();
  }

  @Test
  void rollbackLeavesNothing() {
    tx().executeWithoutResult(s -> {
      journal.record(EventType.TRAP, null, null, "x", null, TS);
      s.setRollbackOnly();
    });
    assertThat(events()).isZero();
  }

  @Test
  void requiredArguments() {
    assertThatThrownBy(() -> tx().executeWithoutResult(s -> journal.record(null, null, null, "x", null, TS)))
        .isInstanceOf(NullPointerException.class);
    assertThatThrownBy(
        () -> tx().executeWithoutResult(s -> journal.record(EventType.TRAP, null, null, null, null, TS)))
        .isInstanceOf(NullPointerException.class);
    assertThatThrownBy(
        () -> tx().executeWithoutResult(s -> journal.record(EventType.TRAP, null, null, "x", null, null)))
        .isInstanceOf(NullPointerException.class);
    assertThat(events()).isZero();
  }
}
