package ru.netmon.auth;

import jakarta.servlet.DispatcherType;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.LoginUrlAuthenticationEntryPoint;
import org.springframework.security.web.authentication.logout.HttpStatusReturningLogoutSuccessHandler;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;

/** Защита по умолчанию (architecture.md §5.1). Права по ресурсам ставятся @PreAuthorize, здесь их нет. */
@Configuration(proxyBeanMethods = false)
@EnableMethodSecurity
class SecurityConfig {

  @Bean
  PasswordEncoder passwordEncoder() {
    return new BCryptPasswordEncoder(10);
  }

  @Bean
  SecurityFilterChain securityFilterChain(HttpSecurity http, JsonAuthHandlers handlers) throws Exception {
    PathPatternRequestMatcher.Builder m = PathPatternRequestMatcher.withDefaults();
    http.authorizeHttpRequests(a -> a
            .dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()
            .requestMatchers(
                m.matcher(HttpMethod.GET, "/login.html"),
                m.matcher(HttpMethod.GET, "/login/**"),
                m.matcher(HttpMethod.GET, "/api/auth/csrf"),
                m.matcher(HttpMethod.POST, "/api/auth/login")).permitAll()
            .anyRequest().authenticated())
        .csrf(c -> c.spa())
        .formLogin(f -> f
            .loginPage("/login.html")
            .loginProcessingUrl("/api/auth/login")
            .successHandler(handlers)
            .failureHandler(handlers))
        .logout(l -> l
            .logoutUrl("/api/auth/logout")
            .logoutSuccessHandler(new HttpStatusReturningLogoutSuccessHandler(HttpStatus.NO_CONTENT))
            .deleteCookies("JSESSIONID"))
        .requestCache(c -> c.disable())
        .exceptionHandling(e -> e
            .defaultAuthenticationEntryPointFor(handlers, m.matcher("/api/**"))
            .defaultAuthenticationEntryPointFor(new LoginUrlAuthenticationEntryPoint("/login.html"),
                m.matcher("/**"))
            .accessDeniedHandler(handlers));
    return http.build();
  }
}
