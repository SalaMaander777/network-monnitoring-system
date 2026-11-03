package ru.netmon.testsupport;

import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Map;
import org.springframework.boot.test.util.TestPropertyValues;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;

/**
 * Встроенный PostgreSQL на один процесс JVM (architecture.md §12.2).
 * Подключается через {@code @ContextConfiguration(initializers = TestPostgres.class)}.
 */
public class TestPostgres implements ApplicationContextInitializer<ConfigurableApplicationContext> {

    private static final class Holder {
        static final EmbeddedPostgres PG = start();

        private static EmbeddedPostgres start() {
            try {
                EmbeddedPostgres pg = EmbeddedPostgres.builder().start();
                Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                    try {
                        pg.close();
                    } catch (IOException ignored) {
                        // завершение JVM
                    }
                }));
                return pg;
            } catch (IOException e) {
                throw new UncheckedIOException("Не удалось запустить встроенный PostgreSQL", e);
            }
        }
    }

    public static String jdbcUrl() {
        return Holder.PG.getJdbcUrl("postgres", "postgres");
    }

    public static Map<String, String> properties() {
        return Map.of(
                "spring.datasource.url", jdbcUrl(),
                "spring.datasource.username", "postgres",
                "spring.datasource.password", "postgres");
    }

    @Override
    public void initialize(ConfigurableApplicationContext context) {
        TestPropertyValues.of(properties()).applyTo(context);
    }
}
