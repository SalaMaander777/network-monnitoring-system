package ru.netmon.schema;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.postgresql.util.PSQLException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.simple.JdbcClient;
import ru.netmon.testsupport.IntegrationTest;

/**
 * T1.02: ограничения схемы проверяются прямым SQL. Без @Transactional: после ошибки ограничения
 * транзакция PostgreSQL прервана. Каждый метод создаёт свои строки с уникальными значениями.
 */
@IntegrationTest
class SchemaConstraintsTest {

    private static final String UNIQUE_VIOLATION = "23505";
    private static final String CHECK_VIOLATION = "23514";

    @Autowired
    JdbcClient jdbc;

    private static final AtomicInteger IP_SEQ = new AtomicInteger(ThreadLocalRandom.current().nextInt(1 << 24));

    /** Уникальный на запуск IPv4 из 10.0.0.0/8 (счётчик со случайным стартом). */
    private static String ip() {
        int v = IP_SEQ.incrementAndGet() & 0xFFFFFF;
        return "10." + ((v >> 16) & 255) + "." + ((v >> 8) & 255) + "." + (v & 255);
    }

    private static String uid() {
        return UUID.randomUUID().toString().substring(0, 8);
    }

    /** Выполняет SQL и сверяет SQLSTATE и имя нарушенного ограничения. */
    private void rejected(String sqlState, String constraint, String sql, Object... args) {
        Throwable thrown = catchThrowable(() -> jdbc.sql(sql).params(args).update());
        assertThat(thrown).as(sql).isInstanceOf(DataAccessException.class);
        Throwable cause = thrown;
        while (cause != null && !(cause instanceof PSQLException)) {
            cause = cause.getCause();
        }
        assertThat(cause).as("PSQLException в цепочке причин: " + sql).isNotNull();
        PSQLException pg = (PSQLException) cause;
        assertThat(pg.getSQLState()).as("SQLSTATE: " + sql).isEqualTo(sqlState);
        assertThat(pg.getServerErrorMessage()).as("сообщение сервера: " + sql).isNotNull();
        assertThat(pg.getServerErrorMessage().getConstraint()).as("ограничение: " + sql).isEqualTo(constraint);
    }

    private long id(String sql, Object... args) {
        return jdbc.sql(sql).params(args).query(Long.class).single();
    }

    private long profile() {
        return id("insert into profile(name) values (?) returning id", "p-" + uid());
    }

    private long type() {
        return id("insert into device_type(name) values (?) returning id", "t-" + uid());
    }

    private long group() {
        return id("insert into device_group(name) values (?) returning id", "g-" + uid());
    }

    private static final String INSERT_DEVICE =
            "insert into device(name,type_id,ip,lat,lon,profile_id) values ('d',?,?::inet,?,?,?)";

    private long device(long typeId, String ip, long profileId) {
        return id(INSERT_DEVICE + " returning id", typeId, ip, 0, 0, profileId);
    }

    @Test
    void tablesAndDefaultPartition() {
        List<String> names = jdbc.sql("""
                select c.relname from pg_class c join pg_namespace n on n.oid = c.relnamespace
                where n.nspname = 'public' and c.relkind in ('r','p')
                  and c.relname <> 'flyway_schema_history'
                """).query(String.class).list();
        assertThat(Set.copyOf(names)).hasSize(names.size()).containsExactlyInAnyOrder(
                "access_secret", "alarm", "app_user", "audit_log", "check_result",
                "check_result_default", "check_state", "device", "device_group", "device_type",
                "event", "profile", "profile_check", "system_setting", "trap_rule");
        assertThat(jdbc.sql("select relkind::text from pg_class where oid = 'public.check_result'::regclass")
                .query(String.class).single()).isEqualTo("p");
        long deviceId = 900_000_000_000L + ThreadLocalRandom.current().nextLong(1_000_000_000L);
        long checkId = 900_000_000_000L + ThreadLocalRandom.current().nextLong(1_000_000_000L);
        long deleted;
        try {
            jdbc.sql("insert into check_result(ts,device_id,check_id,success,duration_ms) values (now(),?,?,true,5)")
                    .params(deviceId, checkId).update();
            assertThat(jdbc.sql("select tableoid::regclass::text from check_result where device_id = ? and check_id = ?")
                    .params(deviceId, checkId).query(String.class).single()).isEqualTo("check_result_default");
        } finally {
            deleted = jdbc.sql("delete from check_result where device_id=? and check_id=?")
                    .params(deviceId, checkId).update();
        }
        assertThat(deleted).isEqualTo(1);
    }

    @Test
    void deviceIpUniqueAmongActive() {
        long t = type();
        long p = profile();
        String ip = ip();
        long first = device(t, ip, p);
        rejected(UNIQUE_VIOLATION, "ux_device_ip_active", INSERT_DEVICE, t, ip, 0, 0, p);
        assertThat(jdbc.sql("update device set deleted_at=now(), profile_id=null where id=?").params(first).update())
                .isEqualTo(1);
        long second = device(t, ip, p);
        assertThat(jdbc.sql("update device set deleted_at=now(), profile_id=null where id=?").params(second).update())
                .isEqualTo(1);
        long third = device(t, ip, p);
        assertThat(third).isNotEqualTo(second);
    }

    @Test
    void deviceIpAndCoordinates() {
        long t = type();
        long p = profile();
        rejected(CHECK_VIOLATION, "device_ip_check", INSERT_DEVICE, t, ip() + "/24", 0, 0, p);
        rejected(CHECK_VIOLATION, "device_ip_check", INSERT_DEVICE, t, "::1", 0, 0, p);
        rejected(CHECK_VIOLATION, "device_lat_check", INSERT_DEVICE, t, ip(), new BigDecimal("90.000001"), 0, p);
        rejected(CHECK_VIOLATION, "device_lon_check", INSERT_DEVICE, t, ip(), 0, new BigDecimal("-180.000001"), p);
        long ok = id(INSERT_DEVICE + " returning id", t, ip(), 90, -180, p);
        assertThat(id("select count(*) from device where id = ? and lat = 90 and lon = -180", ok)).isEqualTo(1);
    }

    @Test
    void profileCheckConstraints() {
        long p = profile();
        String ins = "insert into profile_check(profile_id,code,name,kind,interval_sec,timeout_ms,required,is_primary)"
                + " values (?,?,'n',?,?,1000,?,?)";
        jdbc.sql(ins).params(p, "ping", "PING", 60, true, true).update();
        rejected(UNIQUE_VIOLATION, "ux_profile_check_primary", ins, p, "ping2", "PING", 60, true, true);
        long noPrimary = profile();
        rejected(CHECK_VIOLATION, "profile_check_check", ins, noPrimary, "ping3", "PING", 60, false, true);
        rejected(CHECK_VIOLATION, "profile_check_kind_check", ins, p, "http", "HTTP", 60, true, false);
        rejected(CHECK_VIOLATION, "profile_check_interval_sec_check", ins, p, "fast", "TCP", 4, true, false);
    }

    @Test
    void accessSecretBinding() {
        long t = type();
        long p = profile();
        long d = device(t, ip(), p);
        long g = group();
        rejected(CHECK_VIOLATION, "access_secret_check",
                "insert into access_secret(snmp_version,ciphertext) values ('V2C','\\x00'::bytea)");
        rejected(CHECK_VIOLATION, "access_secret_check",
                "insert into access_secret(snmp_version,ciphertext,device_id,group_id) values ('V2C','\\x00'::bytea,?,?)",
                d, g);
        jdbc.sql("insert into access_secret(snmp_version,ciphertext,device_id) values ('V2C','\\x00'::bytea,?)")
                .params(d).update();
        rejected(UNIQUE_VIOLATION, "ux_secret_device",
                "insert into access_secret(snmp_version,ciphertext,device_id) values ('V3','\\x00'::bytea,?)", d);
    }

    @Test
    void alarmActiveUniqueAndClose() {
        long t = type();
        long p = profile();
        long d = device(t, ip(), p);
        String ins = "insert into alarm(device_id,level,reason_key,reason_text,source,started_at)"
                + " values (?,'WARNING','k','r','CHECK',now())";
        long first = id(ins + " returning id", d);
        rejected(UNIQUE_VIOLATION, "ux_alarm_active", ins, d);
        assertThat(jdbc.sql("update alarm set ended_at=now(), close_reason='RECOVERED' where id=?").params(first)
                .update()).isEqualTo(1);
        long next = id(ins + " returning id", d);
        assertThat(next).isNotEqualTo(first);
        rejected(CHECK_VIOLATION, "alarm_check",
                "insert into alarm(device_id,level,reason_key,reason_text,source,started_at,ended_at)"
                        + " values (?,'WARNING','k2','r','CHECK',now(),now())", d);
    }

    @Test
    void trapRuleConstraints() {
        long p = profile();
        rejected(CHECK_VIOLATION, "trap_rule_check",
                "insert into trap_rule(profile_id,trap_oid,action,pair_key) values (?,'1.3.6.1','ALARM','pk')", p);
        rejected(CHECK_VIOLATION, "trap_rule_check1",
                "insert into trap_rule(profile_id,trap_oid,action) values (?,'1.3.6.2','CLOSE')", p);
        rejected(CHECK_VIOLATION, "trap_rule_trap_oid_check",
                "insert into trap_rule(profile_id,trap_oid,action) values (?,'1.3.6.a','EVENT')", p);
    }

    @Test
    void loginUniqueIgnoringCase() {
        String suffix = uid();
        String insert = "insert into app_user(login,password_hash,role) values (?,'h','ADMIN')";
        jdbc.sql(insert).params("Admin-" + suffix).update();
        rejected(UNIQUE_VIOLATION, "ux_app_user_login", insert, "admin-" + suffix);
    }

    @Test
    void deviceProfileRequiredUnlessDeleted() {
        long t = type();
        String ip = ip();
        rejected(CHECK_VIOLATION, "device_check",
                "insert into device(name,type_id,ip,lat,lon) values ('d',?,?::inet,0,0)", t, ip);
        long deleted = id("insert into device(name,type_id,ip,lat,lon,deleted_at) values ('d',?,?::inet,0,0,now())"
                + " returning id", t, ip);
        assertThat(id("select count(*) from device where id = ? and profile_id is null", deleted)).isEqualTo(1);
    }

    @Test
    void defaultSettings() {
        Map<String, String> actual = new HashMap<>();
        jdbc.sql("select key, value from system_setting where key in ('retention.results_days',"
                + "'retention.journal_days','traps.udp_port','session.timeout_minutes','auth.lock_attempts',"
                + "'auth.lock_minutes')")
                .query(rs -> {
                    actual.put(rs.getString(1), rs.getString(2));
                });
        assertThat(actual).containsExactlyInAnyOrderEntriesOf(Map.of(
                "retention.results_days", "30",
                "retention.journal_days", "365",
                "traps.udp_port", "162",
                "session.timeout_minutes", "720",
                "auth.lock_attempts", "5",
                "auth.lock_minutes", "15"));
    }
}
