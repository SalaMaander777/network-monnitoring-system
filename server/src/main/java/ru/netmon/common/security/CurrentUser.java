package ru.netmon.common.security;

import java.util.Collection;
import java.util.List;
import org.springframework.security.core.CredentialsContainer;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import ru.netmon.contract.Actor;
import ru.netmon.contract.Role;

/** Пользователь сессии (architecture.md §5.1). Хэш пароля после входа обнуляется, в toString его нет. */
public final class CurrentUser implements UserDetails, CredentialsContainer {

  private final long id;
  private final String login;
  private final Role role;
  private final boolean active;
  private String passwordHash;

  public CurrentUser(long id, String login, Role role, boolean active, String passwordHash) {
    this.id = id;
    this.login = login;
    this.role = role;
    this.active = active;
    this.passwordHash = passwordHash;
  }

  public long id() {
    return id;
  }

  public String login() {
    return login;
  }

  public Role role() {
    return role;
  }

  public Actor actor() {
    return new Actor(id, login);
  }

  @Override
  public Collection<? extends GrantedAuthority> getAuthorities() {
    return List.of(new SimpleGrantedAuthority("ROLE_" + role.name()));
  }

  @Override
  public String getPassword() {
    return passwordHash;
  }

  @Override
  public String getUsername() {
    return login;
  }

  @Override
  public boolean isEnabled() {
    return active;
  }

  @Override
  public void eraseCredentials() {
    passwordHash = null;
  }

  @Override
  public String toString() {
    return "CurrentUser[id=" + id + ", login=" + login + ", role=" + role + "]";
  }
}
