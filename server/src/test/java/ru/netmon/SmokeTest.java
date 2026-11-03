package ru.netmon;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import ru.netmon.testsupport.TestPostgres;

@SpringBootTest
@ActiveProfiles("test")
@ContextConfiguration(initializers = TestPostgres.class)
@ExtendWith(OutputCaptureExtension.class)
class SmokeTest {

    @Autowired
    JdbcClient jdbc;

    @Test
    void contextStartsOnPostgres18(CapturedOutput output) {
        String version = jdbc.sql("select version()").query(String.class).single();
        assertThat(version).startsWith("PostgreSQL 18");
        assertThat(output.getAll()).contains("Started SmokeTest in");
        assertThat(output.getAll()).doesNotContain("generated security password");
    }
}
