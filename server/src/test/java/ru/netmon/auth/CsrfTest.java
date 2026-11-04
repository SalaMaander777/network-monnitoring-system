package ru.netmon.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.servlet.http.Cookie;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import ru.netmon.testsupport.IntegrationTest;

@IntegrationTest
class CsrfTest {

  private static final String PASSWORD = "Secret-pass-1";
  private static final String CSRF_INVALID =
      "{\"code\":\"CSRF_INVALID\",\"message\":\"Недействительный токен защиты, обновите страницу\",\"fields\":[]}";

  @Autowired
  MockMvc mvc;

  @Autowired
  JdbcClient jdbc;

  @Autowired
  PasswordEncoder encoder;

  private void insertUser() {
    jdbc.sql("insert into app_user(login, password_hash, role) values ('csrf-user', ?, 'OPERATOR')")
        .param(encoder.encode(PASSWORD)).update();
  }

  private Cookie csrfCookie() throws Exception {
    Cookie c = mvc.perform(get("/api/auth/csrf")).andExpect(status().isNoContent()).andReturn().getResponse()
        .getCookie("XSRF-TOKEN");
    assertThat(c).isNotNull();
    return c;
  }

  private static MockHttpServletRequestBuilder loginRequest(Cookie cookie) {
    MockHttpServletRequestBuilder b = post("/api/auth/login").contentType(MediaType.APPLICATION_FORM_URLENCODED)
        .param("username", "csrf-user").param("password", PASSWORD).cookie(cookie);
    return b;
  }

  private static String body(MvcResult r) throws Exception {
    return r.getResponse().getContentAsString(StandardCharsets.UTF_8);
  }

  private static Cookie lastXsrf(MvcResult r) {
    Cookie last = null;
    for (Cookie c : r.getResponse().getCookies()) {
      if ("XSRF-TOKEN".equals(c.getName())) {
        last = c;
      }
    }
    return last;
  }

  @Test
  void loginWithoutHeaderIs403() throws Exception {
    insertUser();
    MvcResult r = mvc.perform(loginRequest(csrfCookie())).andExpect(status().isForbidden()).andReturn();
    assertThat(body(r)).isEqualTo(CSRF_INVALID);
  }

  @Test
  void foreignTokenIs403() throws Exception {
    insertUser();
    Cookie a = csrfCookie();
    Cookie b = csrfCookie();
    MvcResult foreign = mvc.perform(loginRequest(a).header("X-XSRF-TOKEN", b.getValue()))
        .andExpect(status().isForbidden()).andReturn();
    assertThat(body(foreign)).isEqualTo(CSRF_INVALID);
    mvc.perform(loginRequest(a).header("X-XSRF-TOKEN", a.getValue())).andExpect(status().isOk());
  }

  @Test
  void loginRenewsToken() throws Exception {
    insertUser();
    Cookie used = csrfCookie();
    MvcResult ok = mvc.perform(loginRequest(used).header("X-XSRF-TOKEN", used.getValue()))
        .andExpect(status().isOk()).andReturn();
    Cookie renewed = lastXsrf(ok);
    assertThat(renewed).isNotNull();
    assertThat(renewed.getValue()).isNotEmpty().isNotEqualTo(used.getValue());
    mvc.perform(post("/api/auth/logout").session((MockHttpSession) ok.getRequest().getSession(false))
        .cookie(renewed).header("X-XSRF-TOKEN", renewed.getValue())).andExpect(status().isNoContent());
  }

  @Test
  void logoutWithoutTokenIs403() throws Exception {
    insertUser();
    Cookie used = csrfCookie();
    MvcResult ok = mvc.perform(loginRequest(used).header("X-XSRF-TOKEN", used.getValue()))
        .andExpect(status().isOk()).andReturn();
    MockHttpSession session = (MockHttpSession) ok.getRequest().getSession(false);
    MvcResult denied = mvc.perform(post("/api/auth/logout").session(session)).andExpect(status().isForbidden())
        .andReturn();
    assertThat(body(denied)).isEqualTo(CSRF_INVALID);
    mvc.perform(get("/api/me").session(session)).andExpect(status().isOk());
  }
}
