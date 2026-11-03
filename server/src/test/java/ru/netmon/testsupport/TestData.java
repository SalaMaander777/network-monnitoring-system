package ru.netmon.testsupport;

import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.jdbc.core.simple.JdbcClient;
import ru.netmon.contract.Role;

/** Построители строк прямым SQL с уникальными значениями; возвращают id (architecture.md §12.2). */
public class TestData {

    private static final AtomicInteger IP_SEQ = new AtomicInteger(ThreadLocalRandom.current().nextInt(1 << 24));

    private final JdbcClient jdbc;

    public TestData(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    private static String uid() {
        return UUID.randomUUID().toString().substring(0, 8);
    }

    /** Уникальный на запуск IPv4 из 10.0.0.0/8. */
    public static String ip() {
        int v = IP_SEQ.incrementAndGet() & 0xFFFFFF;
        return "10." + ((v >> 16) & 255) + "." + ((v >> 8) & 255) + "." + (v & 255);
    }

    private long id(String sql, Object... args) {
        return jdbc.sql(sql).params(args).query(Long.class).single();
    }

    public long user(Role role) {
        return id("insert into app_user(login,password_hash,role) values (?,'h',?) returning id",
                "u-" + uid(), role.name());
    }

    public long type() {
        return id("insert into device_type(name) values (?) returning id", "t-" + uid());
    }

    public long group() {
        return id("insert into device_group(name) values (?) returning id", "g-" + uid());
    }

    public long profile() {
        return id("insert into profile(name) values (?) returning id", "p-" + uid());
    }

    /** Проверка PING, основная, обязательная. */
    public long check(long profileId) {
        return id("insert into profile_check(profile_id,code,name,kind,interval_sec,timeout_ms,required,is_primary)"
                + " values (?,?,'n','PING',60,1000,true,true) returning id", profileId, "c-" + uid());
    }

    public long device(long typeId, long profileId) {
        return id("insert into device(name,type_id,ip,lat,lon,profile_id) values (?,?,?::inet,0,0,?) returning id",
                "d-" + uid(), typeId, ip(), profileId);
    }
}
