package ru.netmon.testsupport;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

public class TestDb {
  private static volatile Map<String, String> SNAPSHOT;
  private final JdbcClient jdbc;
  private final TransactionTemplate tx;

  public TestDb(JdbcClient jdbc, PlatformTransactionManager tm) {
    this.jdbc = jdbc;
    this.tx = new TransactionTemplate(tm);
  }

  private Map<String, String> snapshotOnce() {
    Map<String, String> s = SNAPSHOT;
    if (s != null) return s;
    synchronized (TestDb.class) {
      if (SNAPSHOT == null) {
        Map<String, String> m = new LinkedHashMap<>();
        jdbc.sql("select key, value from system_setting order by key").query(rs -> {
          m.put(rs.getString(1), rs.getString(2));
        });
        SNAPSHOT = Collections.unmodifiableMap(m);
      }
      return SNAPSHOT;
    }
  }

  public Map<String, String> defaults() { return snapshotOnce(); }

  public void clean() {
    snapshotOnce();
    tx.executeWithoutResult(st -> {
      jdbc.sql("set local lock_timeout = '5s'").update();
      List<String> t = truncateTargets();
      jdbc.sql("truncate " + String.join(", ", t)).update();
      insertDefaults();
    });
  }

  List<String> truncateTargets() {
    return jdbc.sql("select format('public.%I', relname) from pg_class where relnamespace = 'public'::regnamespace and relkind in ('r','p') and not relispartition and relname <> 'flyway_schema_history' order by relname").query(String.class).list();
  }

  public void restoreSettings() {
    snapshotOnce();
    tx.executeWithoutResult(st -> {
      jdbc.sql("set local lock_timeout = '5s'").update();
      jdbc.sql("delete from system_setting").update();
      insertDefaults();
    });
  }

  private void insertDefaults() {
    for (Map.Entry<String, String> e : snapshotOnce().entrySet()) {
      jdbc.sql("insert into system_setting(key, value) values (?, ?)").params(e.getKey(), e.getValue()).update();
    }
  }

  public static class CleanBeforeEach implements BeforeEachCallback {
    public void beforeEach(ExtensionContext c) {
      var ctx = SpringExtension.getApplicationContext(c);
      ctx.getBean(TestDb.class).clean();
      ctx.getBean(MutableClock.class).reset();
    }
  }
}
