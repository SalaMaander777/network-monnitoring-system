package ru.netmon.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.UnexpectedRollbackException;
import org.springframework.transaction.support.TransactionTemplate;
import ru.netmon.contract.Actor;
import ru.netmon.contract.AuditLog;
import ru.netmon.contract.Role;
import ru.netmon.contract.SnmpCredentials;
import ru.netmon.testsupport.IntegrationTest;
import ru.netmon.testsupport.MutableClock;
import ru.netmon.testsupport.TestData;

@IntegrationTest
class AuditLogTest {

  @Autowired
  AuditLog audit;

  @Autowired
  JdbcClient jdbc;

  @Autowired
  PlatformTransactionManager tm;

  @Autowired
  MutableClock clock;

  @Autowired
  TestData data;

  private TransactionTemplate tx() {
    return new TransactionTemplate(tm);
  }

  private long count(String table) {
    return jdbc.sql("select count(*) from " + table).query(Long.class).single();
  }

  private long groups() {
    return count("device_group");
  }

  @Test
  void recordStoresAllFields() {
    long u = data.user(Role.ADMIN);
    Instant t = Instant.parse("2026-01-02T03:04:05.123456Z");
    clock.set(t);
    long dev = data.device(data.type(), data.profile());
    Actor actor = new Actor(u, "adm-login");
    tx().executeWithoutResult(s -> audit.record(actor, "UPDATE", "device", String.valueOf(dev),
        Map.of("name", "a"), Map.of("name", "b")));
    assertThat(count("audit_log")).isEqualTo(1);
    var row = jdbc.sql("select id, ts, user_id, user_login, action, object_type, object_id,"
        + " old_value = '{\"name\":\"a\"}'::jsonb as o, new_value = '{\"name\":\"b\"}'::jsonb as n from audit_log")
        .query().singleRow();
    assertThat(((java.sql.Timestamp) row.get("ts")).toInstant()).isEqualTo(t);
    assertThat(row.get("user_id")).isEqualTo(u);
    assertThat(row.get("user_login")).isEqualTo("adm-login");
    assertThat(row.get("action")).isEqualTo("UPDATE");
    assertThat(row.get("object_type")).isEqualTo("device");
    assertThat(row.get("object_id")).isEqualTo(String.valueOf(dev));
    assertThat(row.get("o")).isEqualTo(true);
    assertThat(row.get("n")).isEqualTo(true);
    long auditId = (Long) row.get("id");
    assertThat(count("event")).isEqualTo(1);
    var ev = jdbc.sql("select ts, device_id, type, source_ip is null as nosrc, description,"
        + " details = ?::jsonb as d from event")
        .param("{\"auditId\":" + auditId + ",\"userId\":" + u + ",\"login\":\"adm-login\",\"action\":\"UPDATE\","
            + "\"objectType\":\"device\",\"objectId\":\"" + dev + "\"}")
        .query().singleRow();
    assertThat(ev.get("type")).isEqualTo("USER_ACTION");
    assertThat(((java.sql.Timestamp) ev.get("ts")).toInstant()).isEqualTo(t);
    assertThat(ev.get("device_id")).isEqualTo(dev);
    assertThat(ev.get("nosrc")).isEqualTo(true);
    assertThat(ev.get("description")).isEqualTo("Действие пользователя adm-login: UPDATE device " + dev);
    assertThat(ev.get("d")).isEqualTo(true);
  }

  @Test
  void nonDeviceObjects() {
    long u = data.user(Role.ADMIN);
    Actor actor = new Actor(u, "adm-login");
    tx().executeWithoutResult(s -> {
      audit.record(actor, "UPDATE", "setting", "auth.lock_attempts", Map.of("v", "1"), Map.of("v", "2"));
      audit.record(actor, "CREATE", "user", null, null, null);
    });
    assertThat(jdbc.sql("select count(*) from event where device_id is not null").query(Long.class).single())
        .isZero();
    var row = jdbc.sql("select object_id is null a, old_value is null b, new_value is null c from audit_log"
        + " where action = 'CREATE'").query().singleRow();
    assertThat(row.get("a")).isEqualTo(true);
    assertThat(row.get("b")).isEqualTo(true);
    assertThat(row.get("c")).isEqualTo(true);
    assertThat(jdbc.sql("select description from event where description like '%CREATE%'").query(String.class)
        .single()).isEqualTo("Действие пользователя adm-login: CREATE user");
  }

  @Test
  void secretsNotStored() {
    long u = data.user(Role.ADMIN);
    Actor actor = new Actor(u, "adm-login");
    Map<String, Object> oldV = Map.of("password", "S1", "nested", Map.of("community", "S2"), "list",
        List.of(Map.of("authPassword", "S3")));
    Map<String, Object> newV = Map.of("creds", new SnmpCredentials.V3("u-plain", "SHA", "S4", "AES", "S5"),
        "note", "S1-copy");
    tx().executeWithoutResult(s -> audit.record(actor, "UPDATE", "secret", "1", oldV, newV));
    for (String secret : List.of("S2", "S3", "S4", "S5")) {
      for (String table : List.of("audit_log", "event")) {
        assertThat(jdbc.sql("select count(*) from " + table + " t where position(? in t::text) > 0").param(secret)
            .query(Long.class).single()).as(table + " " + secret).isZero();
      }
    }
    assertThat(jdbc.sql("select count(*) from audit_log t where position(? in t::text) > 0").param("S1-copy")
        .query(Long.class).single()).isEqualTo(1);
  }

  @Test
  void rollbackLeavesNothing() {
    long u = data.user(Role.ADMIN);
    tx().executeWithoutResult(s -> {
      jdbc.sql("insert into device_group(name) values (?)").param("g-rb").update();
      audit.record(new Actor(u, "a"), "CREATE", "device_group", "1", null, null);
      s.setRollbackOnly();
    });
    assertThat(count("audit_log")).isZero();
    assertThat(count("event")).isZero();
    assertThat(groups()).isZero();
  }

  @Test
  void commitKeepsBoth() {
    long u = data.user(Role.ADMIN);
    tx().executeWithoutResult(s -> {
      jdbc.sql("insert into device_group(name) values (?)").param("g-ok").update();
      audit.record(new Actor(u, "a"), "CREATE", "device_group", "1", null, null);
    });
    assertThat(groups()).isEqualTo(1);
    assertThat(count("audit_log")).isEqualTo(1);
    assertThat(count("event")).isEqualTo(1);
  }

  @Test
  void requiresTransaction() {
    long u = data.user(Role.ADMIN);
    assertThatThrownBy(() -> audit.record(new Actor(u, "a"), "CREATE", "user", null, null, null))
        .isInstanceOf(IllegalTransactionStateException.class);
    assertThat(count("audit_log")).isZero();
    assertThat(count("event")).isZero();
  }

  @Test
  void failureRollsBackCaller() {
    long u = data.user(Role.ADMIN);
    assertThatThrownBy(() -> tx().executeWithoutResult(s -> {
      jdbc.sql("insert into device_group(name) values (?)").param("g-fail").update();
      try {
        audit.record(new Actor(u, "a"), "bad action", "device_group", "1", null, null);
      } catch (IllegalArgumentException expected) {
        // вызывающий перехватил, транзакция всё равно помечена на откат
      }
    })).isInstanceOf(UnexpectedRollbackException.class);
    assertThat(groups()).isZero();
  }

  @Test
  void argumentChecks() {
    long u = data.user(Role.ADMIN);
    Actor a = new Actor(u, "a");
    for (String action : new String[] {"update", "", null, "A".repeat(65)}) {
      assertThatThrownBy(() -> tx().executeWithoutResult(s -> audit.record(a, action, "device", null, null, null)))
          .isInstanceOf(IllegalArgumentException.class);
    }
    for (String type : new String[] {"Device", "", null}) {
      assertThatThrownBy(() -> tx().executeWithoutResult(s -> audit.record(a, "UPDATE", type, null, null, null)))
          .isInstanceOf(IllegalArgumentException.class);
    }
    assertThatThrownBy(() -> tx().executeWithoutResult(s -> audit.record(null, "UPDATE", "device", null, null, null)))
        .isInstanceOf(NullPointerException.class);
    assertThat(count("audit_log")).isZero();
    assertThat(count("event")).isZero();
  }
}
