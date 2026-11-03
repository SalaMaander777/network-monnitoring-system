package ru.netmon;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

// Автоконфигурация пользователя по умолчанию исключена до T1.05 (AppUserDetailsService):
// иначе Spring Security печатает в лог сгенерированный пароль (architecture.md §3).
@SpringBootApplication(excludeName = "org.springframework.boot.security.autoconfigure.UserDetailsServiceAutoConfiguration")
public class NetMonitoringApplication {

    public static void main(String[] args) {
        SpringApplication.run(NetMonitoringApplication.class, args);
    }
}
