package ru.netmon.testsupport;
import java.lang.annotation.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.*;
@Target(ElementType.TYPE) @Retention(RetentionPolicy.RUNTIME) @Documented
@SpringBootTest @AutoConfigureMockMvc @ActiveProfiles("test")
@ContextConfiguration(initializers = TestPostgres.class)
@Import({MutableClock.Config.class, TestDb.class, TestData.class, Api.class})
@ExtendWith(TestDb.CleanBeforeEach.class)
public @interface IntegrationTest {}
