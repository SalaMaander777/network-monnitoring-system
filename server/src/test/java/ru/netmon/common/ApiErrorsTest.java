package ru.netmon.common;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.util.UUID;
import nmtest.common.ErrorsTestController;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import ru.netmon.contract.Role;
import ru.netmon.testsupport.Api;
import ru.netmon.testsupport.IntegrationTest;
import ru.netmon.testsupport.LogCapture;

@IntegrationTest
@Import(ErrorsTestController.class)
class ApiErrorsTest {

  private static final String BASE = "/api/test-errors";

  @Autowired
  Api api;

  @Autowired
  MockMvc mockMvc;

  @RegisterExtension
  LogCapture logs = new LogCapture();

  private static String body(ResultActions r) throws Exception {
    return r.andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
  }

  private Api.As op() { return api.as(Role.OPERATOR); }

  @Test
  void notFoundFormat() throws Exception {
    assertThat(body(op().get(BASE + "/not-found").andExpect(status().isNotFound())))
        .isEqualTo("{\"code\":\"NOT_FOUND\",\"message\":\"Объект не найден\",\"fields\":[]}");
  }

  @Test
  void conflictWithDetails() throws Exception {
    op().get(BASE + "/conflict").andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("PROFILE_IN_USE"))
        .andExpect(jsonPath("$.message").value("Профиль используется"))
        .andExpect(jsonPath("$.fields").isEmpty())
        .andExpect(jsonPath("$.devices[0].id").value(1));
  }

  @Test
  void validationFields() throws Exception {
    op().post(BASE + "/body", "{\"name\":\"\",\"n\":0}").andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
        .andExpect(jsonPath("$.fields.length()").value(2))
        .andExpect(jsonPath("$.fields[?(@.field=='name')].message").value("Название обязательно"))
        .andExpect(jsonPath("$.fields[?(@.field=='n')].message").value("Не меньше 1"));
    op().get(BASE + "/checked?n=0").andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
        .andExpect(jsonPath("$.fields[0].field").value("n"));
  }

  @Test
  void badJson() throws Exception {
    for (String json : new String[] {"{\"name\":", "{\"name\":\"x\",\"n\":\"abc\"}"}) {
      String b = body(op().post(BASE + "/body", json).andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.code").value("BAD_JSON")).andExpect(jsonPath("$.fields").isEmpty()));
      assertThat(b).doesNotContain("Exception", "jackson", "java.", "ru.netmon");
    }
  }

  @Test
  void typeMismatch() throws Exception {
    String b = body(op().get(BASE + "/typed?n=abc").andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("BAD_PARAMETER"))
        .andExpect(jsonPath("$.fields[0].field").value("n")));
    assertThat(b).doesNotContain("NumberFormat", "java.", "abc");
  }

  @Test
  void springErrors() throws Exception {
    op().get(BASE + "/no-such-path").andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("NOT_FOUND")).andExpect(jsonPath("$.fields").isEmpty());
    op().post(BASE + "/typed", "{}").andExpect(status().isMethodNotAllowed())
        .andExpect(jsonPath("$.code").value("METHOD_NOT_ALLOWED")).andExpect(jsonPath("$.fields").isEmpty());
    mockMvc.perform(post(BASE + "/body").with(user("u").roles("OPERATOR")).with(csrf())
        .contentType(MediaType.TEXT_PLAIN).content("x")).andExpect(status().isUnsupportedMediaType())
        .andExpect(jsonPath("$.code").value("UNSUPPORTED_MEDIA_TYPE")).andExpect(jsonPath("$.fields").isEmpty());
  }

  @Test
  void mixedMethodValidationFieldPaths() throws Exception {
    String json = "{\"name\":\"\",\"checks\":[{\"port\":1},{\"port\":0}]}";
    op().post(BASE + "/mixed/0", json).andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
        .andExpect(jsonPath("$.fields.length()").value(3))
        .andExpect(jsonPath("$.fields[?(@.field=='id')].message").value("Не меньше 1"))
        .andExpect(jsonPath("$.fields[?(@.field=='name')].message").value("Название обязательно"))
        .andExpect(jsonPath("$.fields[?(@.field=='checks[1].port')].message").value("Не меньше 1"))
        .andExpect(jsonPath("$.fields[?(@.field=='m')]").isEmpty());
    op().post(BASE + "/mixed/5", json).andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.fields.length()").value(2))
        .andExpect(jsonPath("$.fields[?(@.field=='name')]").isNotEmpty())
        .andExpect(jsonPath("$.fields[?(@.field=='checks[1].port')]").isNotEmpty());
  }

  @Test
  void stableStatusCodes() throws Exception {
    op().get(BASE + "/status?s=413").andExpect(status().isPayloadTooLarge())
        .andExpect(jsonPath("$.code").value("PAYLOAD_TOO_LARGE"));
    op().get(BASE + "/status?s=422").andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.code").value("HTTP_422"));
    op().get(BASE + "/status?s=418").andExpect(status().is(418))
        .andExpect(jsonPath("$.code").value("HTTP_418"));
    op().get(BASE + "/status?s=406").andExpect(status().isNotAcceptable())
        .andExpect(jsonPath("$.code").value("NOT_ACCEPTABLE"));
    op().get(BASE + "/status?s=503").andExpect(status().isInternalServerError())
        .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"));
  }

  @Test
  void fatalErrorIs500() throws Exception {
    assertThat(body(op().get(BASE + "/fatal").andExpect(status().isInternalServerError())))
        .isEqualTo("{\"code\":\"INTERNAL_ERROR\",\"message\":\"Внутренняя ошибка сервера\",\"fields\":[]}");
  }

  @Test
  void clientGoneAndCommitted() throws Exception {
    assertThat(body(op().get(BASE + "/gone"))).isEmpty();
    op().get(BASE + "/async-timeout").andExpect(status().isServiceUnavailable())
        .andExpect(jsonPath("$.code").value("SERVICE_UNAVAILABLE"))
        .andExpect(jsonPath("$.message").value("Сервис временно недоступен"))
        .andExpect(jsonPath("$.fields").isEmpty());
    String marker = "mk-" + UUID.randomUUID();
    assertThat(body(op().get(BASE + "/committed?m=" + marker))).isEqualTo("data: x\n\n");
    assertThat(body(op().get(BASE + "/committed-abort"))).isEqualTo("data: x\n\n");
    assertThat(logs.text()).contains(marker).doesNotContain(BASE + "/gone", BASE + "/async-timeout",
        BASE + "/committed-abort");
  }

  @Test
  void dataIntegrity() throws Exception {
    String b = body(op().get(BASE + "/db").andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("DATA_CONFLICT")).andExpect(jsonPath("$.fields").isEmpty()));
    assertThat(b).doesNotContain("ux_marker_db", "insert");
    assertThat(logs.text()).contains("Нарушение ограничения БД", BASE + "/db").doesNotContain("ux_marker_db");
  }

  @Test
  void unexpected500() throws Exception {
    String marker = "mk-" + UUID.randomUUID();
    String marker2 = "q-" + UUID.randomUUID();
    String b = body(op().get(BASE + "/boom?m=" + marker + "&q=" + marker2).andExpect(status().isInternalServerError())
        .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
        .andExpect(jsonPath("$.message").value("Внутренняя ошибка сервера"))
        .andExpect(jsonPath("$.fields").isEmpty()));
    assertThat(b).doesNotContain(marker, marker2, "IllegalState", "Exception", "at ");
    assertThat(logs.text()).contains(marker, BASE + "/boom").doesNotContain(marker2);
  }

  @Test
  void accessDeniedGoesToSecurity() throws Exception {
    assertThat(body(op().get(BASE + "/denied").andExpect(status().isForbidden())))
        .doesNotContain("INTERNAL_ERROR");
    assertThat(body(op().get(BASE + "/denied-wrapped").andExpect(status().isForbidden())))
        .doesNotContain("INTERNAL_ERROR");
  }

  @Test
  void pageLimits() throws Exception {
    String[][] bad = {{"size=0", "size"}, {"size=501", "size"}, {"size=-1", "size"}, {"size=abc", "size"},
        {"size=", "size"}, {"page=-1", "page"}, {"page=abc", "page"}};
    for (String[] q : bad) {
      op().get(BASE + "/page?" + q[0]).andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
          .andExpect(jsonPath("$.fields[0].field").value(q[1]));
    }
    op().get(BASE + "/page?size=500").andExpect(status().isOk()).andExpect(jsonPath("$.size").value(500));
    assertThat(body(op().get(BASE + "/page").andExpect(status().isOk())))
        .isEqualTo("{\"items\":[\"name asc, id asc\"],\"page\":0,\"size\":50,\"total\":0}");
    op().get(BASE + "/page?page=2147483647&size=500").andExpect(status().isOk());
  }

  @Test
  void sortWhitelist() throws Exception {
    String[] bad = {"sort=foo", "sort=name;drop table device", "sort=name,ASC", "sort=name,asc,x",
        "sort=name&sort=createdAt"};
    for (String q : bad) {
      op().get(BASE + "/page?" + q).andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.fields[0].field").value("sort"));
    }
    op().get(BASE + "/page?sort=createdAt,desc").andExpect(status().isOk())
        .andExpect(jsonPath("$.items[0]").value("created_at desc, id desc"));
    op().get(BASE + "/page?sort=name").andExpect(status().isOk())
        .andExpect(jsonPath("$.items[0]").value("name asc, id asc"));
  }
}
