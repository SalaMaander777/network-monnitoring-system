package ru.netmon.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import ru.netmon.contract.Actor;
import ru.netmon.contract.AuditLog;
import ru.netmon.contract.Role;
import ru.netmon.testsupport.Api;
import ru.netmon.testsupport.IntegrationTest;
import ru.netmon.testsupport.MutableClock;
import ru.netmon.testsupport.TestData;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@IntegrationTest
class AuditApiTest {

  @Autowired
  Api api;

  @Autowired
  MockMvc mvc;

  @Autowired
  AuditLog audit;

  @Autowired
  JdbcClient jdbc;

  @Autowired
  PlatformTransactionManager tm;

  @Autowired
  MutableClock clock;

  @Autowired
  TestData data;

  @Autowired
  JsonMapper json;

  private long write(Instant ts, long userId, String login, String type, String id, Object o, Object n) {
    clock.set(ts);
    new TransactionTemplate(tm).executeWithoutResult(
        s -> audit.record(new Actor(userId, login), "UPDATE", type, id, o, n));
    return jdbc.sql("select max(id) from audit_log").query(Long.class).single();
  }

  private static String body(ResultActions r) throws Exception {
    return r.andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
  }

  private JsonNode list(String query) throws Exception {
    ResultActions r = api.as(Role.ADMIN).get("/api/audit" + query).andExpect(status().isOk());
    return json.readTree(body(r));
  }

  private List<Long> ids(JsonNode page) {
    List<Long> out = new ArrayList<>();
    page.get("items").forEach(i -> out.add(i.get("id").asLong()));
    return out;
  }

  private void assertField(String query, String field) throws Exception {
    ResultActions r = api.as(Role.ADMIN).get("/api/audit" + query);
    String b = body(r);
    JsonNode t = json.readTree(b);
    assertThat(r.andReturn().getResponse().getStatus()).as(query).isEqualTo(400);
    assertThat(t.get("fields").get(0).get("field").asString()).as(query).isEqualTo(field);
  }

  @Test
  void adminReadsEntry() throws Exception {
    long u = data.user(Role.ADMIN);
    long id = write(Instant.parse("2026-03-01T00:00:10Z"), u, "adm", "setting", "k", Map.of("v", "<b>1</b>"),
        Map.of("v", "2"));
    ResultActions r = api.as(Role.ADMIN).get("/api/audit").andExpect(status().isOk());
    assertThat(body(r)).isEqualTo("{\"items\":[{\"id\":" + id + ",\"ts\":\"2026-03-01T00:00:10Z\",\"userId\":" + u
        + ",\"userLogin\":\"adm\",\"action\":\"UPDATE\",\"objectType\":\"setting\",\"objectId\":\"k\","
        + "\"oldValue\":{\"v\":\"<b>1</b>\"},\"newValue\":{\"v\":\"2\"}}],\"page\":0,\"size\":50,\"total\":1}");
  }

  @Test
  void stablePaging() throws Exception {
    long u = data.user(Role.ADMIN);
    Instant t = Instant.parse("2026-03-01T00:00:00Z");
    long a = write(t, u, "adm", "setting", "k", null, null);
    long b = write(t, u, "adm", "setting", "k", null, null);
    long c = write(t, u, "adm", "setting", "k", null, null);
    long d = write(t.plusSeconds(10), u, "adm", "setting", "k", null, null);
    List<Long> expected = List.of(d, c, b, a);
    List<Long> seen = new ArrayList<>();
    for (int p = 0; p < 4; p++) {
      JsonNode page = list("?page=" + p + "&size=1");
      assertThat(page.get("total").asLong()).isEqualTo(4);
      seen.addAll(ids(page));
    }
    assertThat(seen).containsExactlyElementsOf(expected);
    JsonNode beyond = list("?page=4&size=1");
    assertThat(beyond.get("items").size()).isZero();
    assertThat(beyond.get("total").asLong()).isEqualTo(4);
    assertThat(ids(list("?sort=ts,asc"))).containsExactly(a, b, c, d);
  }

  @Test
  void filters() throws Exception {
    long u1 = data.user(Role.ADMIN);
    long u2 = data.user(Role.ADMIN);
    Instant t0 = Instant.parse("2026-03-01T00:00:00Z");
    Instant t1 = Instant.parse("2026-03-01T00:00:10Z");
    long a = write(t0, u1, "one", "setting", "k", null, null);
    long b = write(t1, u1, "one", "user", "1", null, null);
    long c = write(t0, u2, "two", "user", "2", null, null);
    long d = write(t1, u2, "two", "setting", "k", null, null);
    assertThat(ids(list("?userId=" + u1))).containsExactlyInAnyOrder(a, b);
    assertThat(ids(list("?objectType=user"))).containsExactlyInAnyOrder(b, c);
    assertThat(ids(list("?from=2026-03-01T00:00:10Z"))).containsExactlyInAnyOrder(b, d);
    assertThat(ids(list("?to=2026-03-01T00:00:10Z"))).containsExactlyInAnyOrder(a, c);
    JsonNode combo = list("?userId=" + u2 + "&objectType=user&from=2026-03-01T00:00:00Z&to=2026-03-01T00:00:10Z");
    assertThat(ids(combo)).containsExactly(c);
    assertThat(combo.get("total").asLong()).isEqualTo(1);
    assertThat(list("?from=2026-03-01T00:00:10Z&to=2026-03-01T00:00:10Z").get("total").asLong()).isZero();
    JsonNode unknown = list("?userId=999999999");
    assertThat(unknown.get("total").asLong()).isZero();
    assertThat(unknown.get("items").size()).isZero();
  }

  @Test
  void badParameters() throws Exception {
    api.as(Role.ADMIN).get("/api/audit?from=abc").andExpect(status().isBadRequest());
    assertThat(code("?from=abc")).isEqualTo("BAD_PARAMETER");
    assertField("?from=abc", "from");
    assertThat(code("?from=2026-03-01")).isEqualTo("BAD_PARAMETER");
    assertField("?from=2026-03-01", "from");
    assertThat(code("?to=abc")).isEqualTo("BAD_PARAMETER");
    assertField("?to=abc", "to");
    assertThat(code("?userId=x")).isEqualTo("BAD_PARAMETER");
    assertField("?userId=x", "userId");
    ResultActions r = api.as(Role.ADMIN).get("/api/audit?from=2026-03-02T00:00:00Z&to=2026-03-01T00:00:00Z")
        .andExpect(status().isBadRequest());
    assertThat(body(r)).isEqualTo("{\"code\":\"VALIDATION_FAILED\",\"message\":\"Проверьте введённые данные\","
        + "\"fields\":[{\"field\":null,\"message\":\"Начало периода позже конца\"}]}");
    assertThat(code("?sort=id")).isEqualTo("VALIDATION_FAILED");
    assertField("?sort=id", "sort");
    assertThat(code("?size=0")).isEqualTo("VALIDATION_FAILED");
    assertField("?size=0", "size");
  }

  private String code(String query) throws Exception {
    return json.readTree(body(api.as(Role.ADMIN).get("/api/audit" + query))).get("code").asString();
  }

  @Test
  void rights() throws Exception {
    ResultActions r = api.as(Role.OPERATOR).get("/api/audit").andExpect(status().isForbidden());
    assertThat(body(r)).isEqualTo("{\"code\":\"FORBIDDEN\",\"message\":\"Недостаточно прав\",\"fields\":[]}");
    ResultActions anon = mvc.perform(get("/api/audit")).andExpect(status().isUnauthorized());
    assertThat(json.readTree(body(anon)).get("code").asString()).isEqualTo("AUTH_REQUIRED");
  }

  @Test
  void periodOutOfRange() throws Exception {
    String err = "{\"code\":\"VALIDATION_FAILED\",\"message\":\"Проверьте введённые данные\",\"fields\":[{\"field\":\"%s\",\"message\":\"Дата вне диапазона 0001-01-01 .. 9999-12-31\"}]}";
    String from = err.formatted("from");
    assertThat(body(api.as(Role.ADMIN).get("/api/audit?from={f}", "+10000-01-01T00:00:00Z").andExpect(status().isBadRequest()))).isEqualTo(from);
    assertThat(body(api.as(Role.ADMIN).get("/api/audit?from={f}", "+100000-01-01T00:00:00Z").andExpect(status().isBadRequest()))).isEqualTo(from);
    assertThat(body(api.as(Role.ADMIN).get("/api/audit?from=99999999999999999").andExpect(status().isBadRequest()))).isEqualTo(from);
    assertThat(body(api.as(Role.ADMIN).get("/api/audit?from=0000-12-31T23:59:59.999999Z").andExpect(status().isBadRequest()))).isEqualTo(from);
    assertThat(body(api.as(Role.ADMIN).get("/api/audit?to={t}", "+100000-01-01T00:00:00Z").andExpect(status().isBadRequest()))).isEqualTo(err.formatted("to"));
    assertThat(body(api.as(Role.ADMIN).get("/api/audit?from={f}&to={t}", "+10000-01-01T00:00:00Z", "+10001-01-01T00:00:00Z").andExpect(status().isBadRequest())))
        .isEqualTo("{\"code\":\"VALIDATION_FAILED\",\"message\":\"Проверьте введённые данные\",\"fields\":[{\"field\":\"from\",\"message\":\"Дата вне диапазона 0001-01-01 .. 9999-12-31\"},{\"field\":\"to\",\"message\":\"Дата вне диапазона 0001-01-01 .. 9999-12-31\"}]}");
    api.as(Role.ADMIN).get("/api/audit?from=0001-01-01T00:00:00Z&to=9999-12-31T23:59:59.999999Z").andExpect(status().isOk());
  }
}
