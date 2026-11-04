package ru.netmon.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.servlet.http.Cookie;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import ru.netmon.testsupport.IntegrationTest;
import ru.netmon.testsupport.RestartableApp;

@IntegrationTest
class LoginTest {

  private static final String PASSWORD = "Secret-pass-1";
  private static final String AUTH_FAILED =
      "{\"code\":\"AUTH_FAILED\",\"message\":\"Неверный логин или пароль\",\"fields\":[]}";

  @Autowired
  MockMvc mvc;

  @Autowired
  JdbcClient jdbc;

  @Autowired
  PasswordEncoder encoder;

  private long insertUser(String login, boolean active) {
    return jdbc.sql("insert into app_user(login, password_hash, role, active) values (?, ?, 'OPERATOR', ?) returning id")
        .params(login, encoder.encode(PASSWORD), active).query(Long.class).single();
  }

  private static String body(MvcResult r) throws Exception {
    return r.getResponse().getContentAsString(StandardCharsets.UTF_8);
  }

  private Cookie csrfCookie() throws Exception {
    Cookie c = mvc.perform(get("/api/auth/csrf")).andExpect(status().isNoContent()).andReturn().getResponse()
        .getCookie("XSRF-TOKEN");
    assertThat(c).isNotNull();
    return c;
  }

  private MvcResult login(String login, String password) throws Exception {
    Cookie c = csrfCookie();
    return mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_FORM_URLENCODED)
        .param("username", login).param("password", password).cookie(c).header("X-XSRF-TOKEN", c.getValue()))
        .andReturn();
  }

  private static MockHttpSession session(MvcResult r) {
    return (MockHttpSession) r.getRequest().getSession(false);
  }

  /** Последний Set-Cookie XSRF-TOKEN ответа (первый удаляет прежний токен). */
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
  void anonymousIsRejected() throws Exception {
    String expected = "{\"code\":\"AUTH_REQUIRED\",\"message\":\"Требуется вход в систему\",\"fields\":[]}";
    MvcResult me = mvc.perform(get("/api/me")).andExpect(status().isUnauthorized()).andReturn();
    assertThat(body(me)).isEqualTo(expected);
    assertThat(me.getRequest().getSession(false)).isNull();
    assertThat(body(mvc.perform(get("/api/no-such")).andExpect(status().isUnauthorized()).andReturn()))
        .isEqualTo(expected);
    MvcResult page = mvc.perform(get("/index.html")).andExpect(status().isFound()).andReturn();
    assertThat(page.getResponse().getHeader("Location")).endsWith("/login.html");
    for (String path : new String[] {"/login.html", "/login/x.js"}) {
      int s = mvc.perform(get(path)).andReturn().getResponse().getStatus();
      assertThat(s).as(path).isNotIn(401, 302);
    }
  }

  @Test
  void loginReturnsUser() throws Exception {
    long id = insertUser("Ivan", true);
    MvcResult ok = login("IVAN", PASSWORD);
    assertThat(ok.getResponse().getStatus()).isEqualTo(200);
    assertThat(body(ok)).isEqualTo("{\"login\":\"Ivan\",\"role\":\"OPERATOR\"}");
    MvcResult me = mvc.perform(get("/api/me").session(session(ok))).andExpect(status().isOk()).andReturn();
    assertThat(body(me)).isEqualTo("{\"id\":" + id + ",\"login\":\"Ivan\",\"role\":\"OPERATOR\"}");
  }

  @Test
  void loginFailuresAreUniform() throws Exception {
    insertUser("ivan", true);
    insertUser("off", false);
    String[][] cases = {{"ivan", "wrong"}, {"nobody", PASSWORD}, {"off", PASSWORD}, {"", ""},
        {"x".repeat(1000), PASSWORD}};
    for (String[] c : cases) {
      MvcResult r = login(c[0], c[1]);
      assertThat(r.getResponse().getStatus()).as(c[0].length() > 20 ? "long login" : c[0]).isEqualTo(401);
      assertThat(body(r)).isEqualTo(AUTH_FAILED);
    }
  }

  @Test
  void passwordStoredAsBcrypt() {
    long id = insertUser("bcrypt-user", true);
    String hash = jdbc.sql("select password_hash from app_user where id = ?").param(id).query(String.class).single();
    assertThat(hash).startsWith("$2a$10$");
    long hits = jdbc.sql("select count(*) from app_user t where position(? in t::text) > 0").param(PASSWORD)
        .query(Long.class).single();
    assertThat(hits).isZero();
  }

  @Test
  void logoutEndsSession() throws Exception {
    insertUser("ivan", true);
    MvcResult ok = login("ivan", PASSWORD);
    MockHttpSession session = session(ok);
    Cookie xsrf = lastXsrf(ok);
    mvc.perform(post("/api/auth/logout").session(session).cookie(xsrf).header("X-XSRF-TOKEN", xsrf.getValue()))
        .andExpect(status().isNoContent());
    mvc.perform(get("/api/me").session(session)).andExpect(status().isUnauthorized());
  }

  @Test
  void sessionIdChangesOnLogin() throws Exception {
    insertUser("tomcat-user", true);
    try (RestartableApp app = new RestartableApp(Map.of()).start()) {
      HttpClient client = HttpClient.newHttpClient();
      String base = "http://127.0.0.1:" + app.port();

      HttpResponse<String> csrf = client.send(HttpRequest.newBuilder(URI.create(base + "/api/auth/csrf")).build(),
          HttpResponse.BodyHandlers.ofString());
      String token = cookieValue(csrf, "XSRF-TOKEN");

      HttpResponse<String> first = formLogin(client, base, "JSESSIONID=none-yet; XSRF-TOKEN=" + token, token);
      assertThat(first.statusCode()).isEqualTo(200);
      String sessionCookie = setCookie(first, "JSESSIONID");
      assertThat(sessionCookie).contains("HttpOnly").contains("SameSite=Lax");
      String firstId = sessionCookie.split(";")[0];
      String nextToken = cookieValue(first, "XSRF-TOKEN");

      HttpResponse<String> second = formLogin(client, base, firstId + "; XSRF-TOKEN=" + nextToken, nextToken);
      assertThat(second.statusCode()).isEqualTo(200);
      String secondId = setCookie(second, "JSESSIONID").split(";")[0];
      assertThat(secondId).isNotEqualTo(firstId);

      HttpResponse<String> old = client.send(HttpRequest.newBuilder(URI.create(base + "/api/me"))
          .header("Cookie", firstId).build(), HttpResponse.BodyHandlers.ofString());
      assertThat(old.statusCode()).isEqualTo(401);
      HttpResponse<String> current = client.send(HttpRequest.newBuilder(URI.create(base + "/api/me"))
          .header("Cookie", secondId).build(), HttpResponse.BodyHandlers.ofString());
      assertThat(current.statusCode()).isEqualTo(200);
    }
  }

  private static HttpResponse<String> formLogin(HttpClient client, String base, String cookie, String token)
      throws Exception {
    return client.send(HttpRequest.newBuilder(URI.create(base + "/api/auth/login"))
        .header("Content-Type", "application/x-www-form-urlencoded").header("Cookie", cookie)
        .header("X-XSRF-TOKEN", token)
        .POST(HttpRequest.BodyPublishers.ofString("username=tomcat-user&password=" + PASSWORD)).build(),
        HttpResponse.BodyHandlers.ofString());
  }

  private static String setCookie(HttpResponse<?> r, String name) {
    List<String> all = r.headers().allValues("set-cookie");
    return all.stream().filter(v -> v.startsWith(name + "=") && !v.startsWith(name + "=;")).reduce((a, b) -> b)
        .orElseThrow(() -> new AssertionError("нет Set-Cookie " + name + ": " + all));
  }

  private static String cookieValue(HttpResponse<?> r, String name) {
    return setCookie(r, name).split(";")[0].split("=", 2)[1];
  }
}
