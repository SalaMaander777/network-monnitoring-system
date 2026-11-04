package ru.netmon.auth;

import java.util.Optional;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import ru.netmon.common.security.CurrentUser;
import ru.netmon.contract.Role;

@Repository
class AppUserRepository {

  private final JdbcClient jdbc;

  AppUserRepository(JdbcClient jdbc) {
    this.jdbc = jdbc;
  }

  Optional<CurrentUser> findByLogin(String login) {
    return jdbc.sql("select id, login, role, active, password_hash from app_user where lower(login) = lower(?)")
        .param(login)
        .query((rs, i) -> new CurrentUser(rs.getLong("id"), rs.getString("login"),
            Role.valueOf(rs.getString("role")), rs.getBoolean("active"), rs.getString("password_hash")))
        .optional();
  }
}
