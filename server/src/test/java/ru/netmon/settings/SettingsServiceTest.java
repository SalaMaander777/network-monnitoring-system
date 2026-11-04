package ru.netmon.settings;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import ru.netmon.common.web.ApiException;
import ru.netmon.contract.Actor;
import ru.netmon.contract.Role;
import ru.netmon.testsupport.IntegrationTest;
import ru.netmon.testsupport.TestData;
import ru.netmon.testsupport.TestDb;

@IntegrationTest
class SettingsServiceTest {
  @Autowired SettingsService service;
  @Autowired JdbcClient jdbc;
  @Autowired PlatformTransactionManager tm;
  @Autowired TestData data;
  @Autowired TestDb db;

  @AfterEach
  void restore() { db.restoreSettings(); }

  private Actor admin() { return new Actor(data.user(Role.ADMIN), "adm"); }

  @Test
  void rollbackKeepsValue() {
    Actor a = admin();
    new TransactionTemplate(tm).executeWithoutResult(s -> {
      service.update("auth.lock_minutes", "60", a);
      assertThat(service.getInt("auth.lock_minutes")).isEqualTo(60);
      s.setRollbackOnly();
    });
    assertThat(service.getInt("auth.lock_minutes")).isEqualTo(15);
    assertThat(jdbc.sql("select count(*) from audit_log").query(Long.class).single()).isZero();
  }

  @Test
  void unknownKeyForReaders() {
    assertThatThrownBy(() -> service.get("no.such")).isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> service.getInt("no.such")).isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void invalidStoredValue() {
    jdbc.sql("update system_setting set value = '0' where key = 'auth.lock_attempts'").update();
    assertThatThrownBy(() -> service.getInt("auth.lock_attempts")).isInstanceOf(IllegalStateException.class)
        .hasMessage("Недопустимое значение настройки auth.lock_attempts в БД");
    assertThat(service.get("auth.lock_attempts")).isEqualTo("0");
  }

  @Test
  void missingRow() {
    jdbc.sql("delete from system_setting where key = 'auth.lock_attempts'").update();
    assertThatThrownBy(() -> service.getInt("auth.lock_attempts")).isInstanceOf(IllegalStateException.class);
    assertThatThrownBy(() -> service.list()).isInstanceOf(IllegalStateException.class);
    assertThatThrownBy(() -> service.update("auth.lock_attempts", "3", admin())).isInstanceOf(IllegalStateException.class);
  }

  @Test
  void validationThrowsApiException() {
    assertThatThrownBy(() -> service.update("auth.lock_attempts", "0", admin())).isInstanceOf(ApiException.class);
    assertThatThrownBy(() -> service.update("nope", "1", admin())).isInstanceOf(ApiException.class)
        .extracting(e -> ((ApiException) e).code()).isEqualTo("NOT_FOUND");
  }

  @Test
  void concurrentUpdatesSerialized() throws Exception {
    Actor a = admin();
    CountDownLatch firstWritten = new CountDownLatch(1);
    CountDownLatch release = new CountDownLatch(1);
    ExecutorService ex = Executors.newFixedThreadPool(2);
    try {
      Future<?> f1 = ex.submit(() -> new TransactionTemplate(tm).executeWithoutResult(s -> {
        service.update("auth.lock_attempts", "10", a);
        firstWritten.countDown();
        try { release.await(10, TimeUnit.SECONDS); } catch (InterruptedException e) { throw new RuntimeException(e); }
      }));
      assertThat(firstWritten.await(10, TimeUnit.SECONDS)).isTrue();
      Future<?> f2 = ex.submit(() -> service.update("auth.lock_attempts", "20", a));
      Instant deadline = Instant.now().plus(Duration.ofSeconds(10));
      while (jdbc.sql("select count(*) from pg_stat_activity where wait_event_type = 'Lock' and query like 'select value from system_setting%'")
          .query(Long.class).single() == 0) {
        assertThat(Instant.now()).isBefore(deadline);
        Thread.onSpinWait();
      }
      release.countDown();
      f1.get(10, TimeUnit.SECONDS);
      f2.get(10, TimeUnit.SECONDS);
    } finally {
      release.countDown();
      ex.shutdownNow();
    }
    List<String> rows = jdbc.sql("select old_value->>'value' || '>' || (new_value->>'value') from audit_log order by id")
        .query(String.class).list();
    assertThat(rows).containsExactly("5>10", "10>20");
    assertThat(service.getInt("auth.lock_attempts")).isEqualTo(20);
  }
}
