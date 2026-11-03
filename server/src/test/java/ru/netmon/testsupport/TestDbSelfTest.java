package ru.netmon.testsupport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import static org.junit.jupiter.api.Assertions.assertThrows;
import java.util.LinkedHashMap;
import java.util.Map;
import ch.qos.logback.classic.Level;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;
import ru.netmon.contract.Role;

@IntegrationTest
class TestDbSelfTest {

    /** Шесть строк INSERT из architecture.md §4.2. */
    private static final Map<String, String> DEFAULTS = new LinkedHashMap<>();

    static {
        DEFAULTS.put("retention.results_days", "30");
        DEFAULTS.put("retention.journal_days", "365");
        DEFAULTS.put("traps.udp_port", "162");
        DEFAULTS.put("session.timeout_minutes", "720");
        DEFAULTS.put("auth.lock_attempts", "5");
        DEFAULTS.put("auth.lock_minutes", "15");
    }

    @Autowired
    JdbcClient jdbc;
    @Autowired
    TestDb db;
    @Autowired
    TestData data;
    @Autowired
    Api api;
    @Autowired
    Clock clock;
    @Autowired
    MutableClock mutableClock;
    @Autowired
    DataSource dataSource;

    @AfterEach
    void restore() {
        db.restoreSettings();
    }

    @RegisterExtension
    LogCapture logs = new LogCapture();

    private long count(String table) {
        return jdbc.sql("select count(*) from " + table).query(Long.class).single();
    }

    private Map<String, String> settings() {
        Map<String, String> m = new LinkedHashMap<>();
        jdbc.sql("select key, value from system_setting order by key").query(rs -> {
            m.put(rs.getString(1), rs.getString(2));
        });
        return m;
    }

    private void assertSettings() {
        assertThat(settings()).isEqualTo(db.defaults()).containsAllEntriesOf(DEFAULTS).doesNotContainKey("extra");
        assertThat(db.defaults()).containsAllEntriesOf(DEFAULTS);
    }

    private void dirty() {
        long user = data.user(Role.ADMIN);
        long profile = data.profile();
        data.check(profile);
        data.device(data.type(), profile);
        data.group();
        jdbc.sql("insert into check_result(ts,device_id,check_id,success,duration_ms) values (now(),1,1,true,1)")
                .update();
        assertThat(jdbc.sql("update system_setting set value='1', updated_by=? where key='auth.lock_attempts'")
                .params(user).update()).isEqualTo(1);
        jdbc.sql("insert into system_setting(key,value) values ('extra','1')").update();
    }

    @Test
    void cleanRemovesDataAndRestoresSettings() {
        try {
            dirty();
            assertThat(count("check_result_default")).isEqualTo(1);
            db.clean();
            for (String t : new String[] {"app_user", "profile", "profile_check", "device", "device_type",
                    "device_group", "check_result", "check_result_default"}) {
                assertThat(count(t)).as(t).isZero();
            }
            assertSettings();
            assertThat(jdbc.sql("select count(*) from system_setting where updated_by is not null")
                    .query(Long.class).single()).isZero();
        } finally {
            // §12.2: при падении проверки строки не должны остаться в общей БД
            db.clean();
        }
    }

    @Test
    void restoreSettingsOnly() {
        int removed;
        try {
            dirty();
            db.restoreSettings();
            assertSettings();
            assertThat(count("app_user")).isEqualTo(1);
        } finally {
            // §12.2: строку check_result_default удаляет тот же тест
            removed = jdbc.sql("delete from check_result_default").update();
        }
        assertThat(removed).isEqualTo(1);
    }

    @Test
    void helpersWork() throws Exception {
        assertThat(clock).isSameAs(mutableClock);
        Instant before = clock.instant();
        mutableClock.advance(Duration.ofSeconds(5));
        assertThat(clock.instant()).isEqualTo(before.plusSeconds(5));
        assertThat(api.as(Role.ADMIN)).isNotNull();
    }

    @Test
    void clockResetAndZoneView() {
        mutableClock.set(Instant.EPOCH);
        mutableClock.reset();
        assertThat(mutableClock.instant()).isAfter(Instant.EPOCH);
        java.time.ZoneId msk = java.time.ZoneId.of("Europe/Moscow");
        Clock view = mutableClock.withZone(msk);
        assertThat(view.getZone()).isEqualTo(msk);
        assertThat(view.instant()).isEqualTo(mutableClock.instant());
        mutableClock.advance(Duration.ofSeconds(7));
        assertThat(view.instant()).isEqualTo(mutableClock.instant());
        assertThat(mutableClock.withZone(java.time.ZoneOffset.UTC)).isSameAs(mutableClock);
    }

    @Test
    void logLevelIsCaptured() {
        logs.level("probe.debug", Level.DEBUG);
        LoggerFactory.getLogger("probe.debug").debug("debug-marker");
        assertThat(logs.text()).contains("debug-marker");
    }

    @Test
    void cleanFailsFastOnLockedTable() throws Throwable {
        Connection c = dataSource.getConnection();
        ExecutorService pool = Executors.newSingleThreadExecutor();
        Throwable primary = null;
        boolean manual = false;
        try {
            c.setAutoCommit(false);
            manual = true;
            try (Statement st = c.createStatement()) {
                st.execute("lock table device_type in access share mode");
            }
            Future<?> future = pool.submit(() -> db.clean());
            ExecutionException thrown = assertThrows(ExecutionException.class, () -> future.get(30, TimeUnit.SECONDS));
            Throwable root = thrown;
            while (root.getCause() != null && root.getCause() != root) {
                root = root.getCause();
            }
            assertThat(root).isInstanceOf(SQLException.class);
            assertThat(((SQLException) root).getSQLState()).isEqualTo("55P03");
        } catch (Throwable t) {
            primary = t;
            throw t;
        } finally {
            Throwable cleanup = null;
            try {
                try {
                    if (manual) {
                        c.rollback();
                    }
                } finally {
                    c.close();
                }
            } catch (Throwable t) {
                cleanup = t;
            }
            try {
                pool.shutdown();
                if (!pool.awaitTermination(30, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("clean() не завершился после снятия блокировки");
                }
            } catch (Throwable t) {
                if (cleanup == null) {
                    cleanup = t;
                } else {
                    cleanup.addSuppressed(t);
                }
            }
            if (cleanup != null) {
                if (primary == null) {
                    throw cleanup;
                }
                primary.addSuppressed(cleanup);
            }
        }
    }

    @Test
    void clockZoneEquality() {
        Clock utc = mutableClock.withZone(ZoneId.of("UTC"));
        assertThat(utc.getZone()).isEqualTo(ZoneId.of("UTC")).isNotEqualTo(ZoneOffset.UTC);
        Clock again = mutableClock.withZone(ZoneId.of("UTC"));
        assertThat(utc).isEqualTo(again).hasSameHashCodeAs(again);
        assertThat(utc).isNotEqualTo(mutableClock.withZone(ZoneId.of("Europe/Moscow")));
        assertThat(mutableClock.withZone(ZoneOffset.UTC)).isSameAs(mutableClock);
    }

    @Test
    void truncateTargetsQualified() {
        List<String> t = db.truncateTargets();
        assertThat(t).allMatch(n -> n.startsWith("public."));
        assertThat(t).contains("public.check_result", "public.system_setting")
                .doesNotContain("public.check_result_default", "public.flyway_schema_history");
    }

    @Test
    void restartableApp() {
        jdbc.sql("insert into app_user(login,password_hash,role) values ('restart-user','h','ADMIN')").update();
        try (RestartableApp app = new RestartableApp(Map.of()).start()) {
            assertThat(app.port()).isPositive().isNotEqualTo(8080);
            assertThatThrownBy(app::start).isInstanceOf(IllegalStateException.class);
            app.restart();
            assertThat(app.port()).isPositive().isNotEqualTo(8080);
            LoggerFactory.getLogger("probe").info("after-restart-marker");
            assertThat(app.context().getBean(JdbcClient.class)
                    .sql("select count(*) from app_user where login='restart-user'")
                    .query(Long.class).single()).isEqualTo(1);
            assertThat(app.context().getBean(Clock.class)).isNotInstanceOf(MutableClock.class);
        }
        LoggerFactory.getLogger("probe").info("after-stop-marker");
        assertThat(logs.text()).contains("after-restart-marker").contains("after-stop-marker").contains("Started");
    }
}
