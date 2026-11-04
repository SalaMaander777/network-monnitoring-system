package ru.netmon.settings;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import ru.netmon.contract.Role;
import ru.netmon.contract.Settings;
import ru.netmon.testsupport.Api;
import ru.netmon.testsupport.IntegrationTest;
import ru.netmon.testsupport.RestartableApp;
import ru.netmon.testsupport.TestDb;

@IntegrationTest
class SettingsApiTest {
  @Autowired Api api;
  @Autowired MockMvc mvc;
  @Autowired Settings settings;
  @Autowired JdbcClient jdbc;
  @Autowired TestDb db;

  @AfterEach
  void restore() { db.restoreSettings(); }

  private static String body(ResultActions r) throws Exception {
    return r.andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
  }

  private ResultActions putValue(String key, String json) throws Exception {
    return api.as(Role.ADMIN).put("/api/settings/" + key, json);
  }

  @Test
  void listsAllDefinedKeys() throws Exception {
    ResultActions r = api.as(Role.ADMIN).get("/api/settings").andExpect(status().isOk());
    assertThat(body(r)).isEqualTo("[{\"key\":\"auth.lock_attempts\",\"value\":\"5\",\"min\":1,\"max\":100},"
        + "{\"key\":\"auth.lock_minutes\",\"value\":\"15\",\"min\":1,\"max\":1440},"
        + "{\"key\":\"retention.journal_days\",\"value\":\"365\",\"min\":1,\"max\":3650},"
        + "{\"key\":\"retention.results_days\",\"value\":\"30\",\"min\":1,\"max\":3650},"
        + "{\"key\":\"session.timeout_minutes\",\"value\":\"720\",\"min\":1,\"max\":43200},"
        + "{\"key\":\"traps.udp_port\",\"value\":\"162\",\"min\":1,\"max\":65535}]");
  }

  @Test
  void updateAppliesImmediately() throws Exception {
    ResultActions r = putValue("auth.lock_attempts", "{\"value\":\"7\"}").andExpect(status().isOk());
    assertThat(body(r)).isEqualTo("{\"key\":\"auth.lock_attempts\",\"value\":\"7\",\"min\":1,\"max\":100}");
    assertThat(settings.getInt("auth.lock_attempts")).isEqualTo(7);
    assertThat(settings.get("auth.lock_attempts")).isEqualTo("7");
    Long by = jdbc.sql("select updated_by from system_setting where key = 'auth.lock_attempts'").query(Long.class).single();
    Long admin = jdbc.sql("select id from app_user where login = 'test-admin'").query(Long.class).single();
    assertThat(by).isEqualTo(admin);
  }

  @Test
  void bodyForms() throws Exception {
    assertThat(body(putValue("auth.lock_attempts", "{\"value\":8}").andExpect(status().isOk()))).contains("\"value\":\"8\"");
    putValue("auth.lock_attempts", "{\"value\":\"9\",\"extra\":1}").andExpect(status().isOk());
    for (String b : new String[] {"{\"value\":true}", "{\"value\":8.0}"}) {
      assertThat(body(putValue("auth.lock_attempts", b).andExpect(status().isBadRequest()))).contains("VALIDATION_FAILED", "\"field\":\"value\"");
    }
    for (String b : new String[] {"{\"value\":{}}", "[]", null}) {
      assertThat(body(api.as(Role.ADMIN).put("/api/settings/auth.lock_attempts", b).andExpect(status().isBadRequest()))).contains("BAD_JSON");
    }
    assertThat(body(putValue("auth.lock_attempts.json", "{\"value\":\"9\"}").andExpect(status().isNotFound()))).contains("NOT_FOUND");
    putValue("AUTH.LOCK_ATTEMPTS", "{\"value\":\"9\"}").andExpect(status().isNotFound());
    api.as(Role.ADMIN).get("/api/settings/auth.lock_attempts").andExpect(status().isMethodNotAllowed());
  }

  @Test
  void bounds() throws Exception {
    Map<String, int[]> b = Map.of("retention.results_days", new int[] {1, 3650}, "retention.journal_days",
        new int[] {1, 3650}, "traps.udp_port", new int[] {1, 65535}, "session.timeout_minutes", new int[] {1, 43200},
        "auth.lock_attempts", new int[] {1, 100}, "auth.lock_minutes", new int[] {1, 1440});
    for (var e : b.entrySet()) {
      String k = e.getKey();
      int min = e.getValue()[0], max = e.getValue()[1];
      putValue(k, "{\"value\":\"" + min + "\"}").andExpect(status().isOk());
      assertThat(settings.getInt(k)).isEqualTo(min);
      putValue(k, "{\"value\":\"" + max + "\"}").andExpect(status().isOk());
      assertThat(settings.getInt(k)).isEqualTo(max);
      String expected = "{\"code\":\"VALIDATION_FAILED\",\"message\":\"Проверьте введённые данные\",\"fields\":[{\"field\":\"value\",\"message\":\"Целое число от " + min + " до " + max + "\"}]}";
      for (String bad : new String[] {"\"" + (min - 1) + "\"", "\"" + (max + 1L) + "\"", "\"\"", "\"abc\"", "\" 5\"",
          "\"05\"", "\"+5\"", "\"-1\"", "\"1.0\"", "\"99999999999\"", "null"}) {
        ResultActions r = putValue(k, "{\"value\":" + bad + "}").andExpect(status().isBadRequest());
        assertThat(body(r)).as(k + " " + bad).isEqualTo(expected);
      }
      ResultActions r = putValue(k, "{}").andExpect(status().isBadRequest());
      assertThat(body(r)).isEqualTo(expected);
      assertThat(settings.getInt(k)).isEqualTo(max);
    }
  }

  @Test
  void unknownKey() throws Exception {
    ResultActions r = putValue("no.such_key", "{\"value\":\"1\"}").andExpect(status().isNotFound());
    assertThat(body(r)).isEqualTo("{\"code\":\"NOT_FOUND\",\"message\":\"Объект не найден\",\"fields\":[]}");
    r = putValue("no.such_key", "{\"value\":\"abc\"}").andExpect(status().isNotFound());
    assertThat(body(r)).isEqualTo("{\"code\":\"NOT_FOUND\",\"message\":\"Объект не найден\",\"fields\":[]}");
    assertThat(jdbc.sql("select count(*) from system_setting").query(Long.class).single()).isEqualTo(6);
    assertThat(jdbc.sql("select count(*) from audit_log").query(Long.class).single()).isZero();
  }

  @Test
  void auditRecordsOldAndNew() throws Exception {
    putValue("retention.results_days", "{\"value\":\"45\"}").andExpect(status().isOk());
    putValue("retention.results_days", "{\"value\":\"45\"}").andExpect(status().isOk());
    var rows = jdbc.sql("select user_login, action, object_type, object_id, old_value::text o, new_value::text n from audit_log")
        .query((rs, i) -> rs.getString(1) + "|" + rs.getString(2) + "|" + rs.getString(3) + "|" + rs.getString(4) + "|"
            + rs.getString(5) + "|" + rs.getString(6)).list();
    assertThat(rows).containsExactly("test-admin|UPDATE|setting|retention.results_days|{\"value\": \"30\"}|{\"value\": \"45\"}");
    assertThat(jdbc.sql("select count(*) from event where type = 'USER_ACTION' and device_id is null").query(Long.class).single()).isEqualTo(1);
  }

  @Test
  void rights() throws Exception {
    ResultActions r = api.as(Role.OPERATOR).get("/api/settings").andExpect(status().isForbidden());
    assertThat(body(r)).isEqualTo("{\"code\":\"FORBIDDEN\",\"message\":\"Недостаточно прав\",\"fields\":[]}");
    api.as(Role.OPERATOR).put("/api/settings/auth.lock_attempts", "{\"value\":\"7\"}").andExpect(status().isForbidden());
    mvc.perform(get("/api/settings")).andExpect(status().isUnauthorized());
    mvc.perform(put("/api/settings/auth.lock_attempts").with(api.csrfToken()).contentType(MediaType.APPLICATION_JSON)
        .content("{\"value\":\"7\"}")).andExpect(status().isUnauthorized());
    assertThat(settings.getInt("auth.lock_attempts")).isEqualTo(5);
    assertThat(jdbc.sql("select count(*) from audit_log").query(Long.class).single()).isZero();
  }

  @Test
  void persistsAcrossRestartAndOtherContext() throws Exception {
    try (RestartableApp app = new RestartableApp(Map.of()).start()) {
      Settings other = app.context().getBean(Settings.class);
      assertThat(other.getInt("session.timeout_minutes")).isEqualTo(720);
      putValue("session.timeout_minutes", "{\"value\":\"30\"}").andExpect(status().isOk());
      assertThat(other.getInt("session.timeout_minutes")).isEqualTo(30);
      app.restart();
      assertThat(app.context().getBean(Settings.class).getInt("session.timeout_minutes")).isEqualTo(30);
    }
  }
}
