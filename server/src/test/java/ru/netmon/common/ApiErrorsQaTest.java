package ru.netmon.common;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.util.UUID;
import nmtest.common.ErrorsTestController;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.ResultActions;
import ru.netmon.contract.Role;
import ru.netmon.testsupport.Api;
import ru.netmon.testsupport.IntegrationTest;

/** Дополнительные проверки QA: тексты и коды таблицы architecture.md 5.1, строгая сортировка, утечки. */
@IntegrationTest
@Import(ErrorsTestController.class)
class ApiErrorsQaTest {

  private static final String BASE = "/api/test-errors";

  @Autowired
  Api api;

  private static String body(ResultActions r) throws Exception {
    return r.andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
  }

  private Api.As op() { return api.as(Role.OPERATOR); }

  @Test
  void literalMessagesAndContentType() throws Exception {
    assertThat(body(op().post(BASE + "/body", "{\"name\":\"\",\"n\":0}").andExpect(status().isBadRequest())))
        .startsWith("{\"code\":\"VALIDATION_FAILED\",\"message\":\"Проверьте введённые данные\",\"fields\":[");
    assertThat(body(op().get(BASE + "/checked?n=0").andExpect(status().isBadRequest())))
        .startsWith("{\"code\":\"VALIDATION_FAILED\",\"message\":\"Проверьте введённые данные\"");
    assertThat(body(op().post(BASE + "/body", "{\"name\":").andExpect(status().isBadRequest())))
        .isEqualTo("{\"code\":\"BAD_JSON\",\"message\":\"Тело запроса отсутствует или не является корректным JSON\",\"fields\":[]}");
    assertThat(body(op().get(BASE + "/typed?n=abc").andExpect(status().isBadRequest())))
        .isEqualTo("{\"code\":\"BAD_PARAMETER\",\"message\":\"Неверное значение параметра\",\"fields\":[{\"field\":\"n\",\"message\":\"Неверное значение\"}]}");
    assertThat(body(op().get(BASE + "/db").andExpect(status().isConflict())))
        .isEqualTo("{\"code\":\"DATA_CONFLICT\",\"message\":\"Данные противоречат существующим записям\",\"fields\":[]}");
    assertThat(body(op().get(BASE + "/boom?m=x").andExpect(status().isInternalServerError())))
        .isEqualTo("{\"code\":\"INTERNAL_ERROR\",\"message\":\"Внутренняя ошибка сервера\",\"fields\":[]}");
    assertThat(body(op().get(BASE + "/no-such-path").andExpect(status().isNotFound())))
        .isEqualTo("{\"code\":\"NOT_FOUND\",\"message\":\"Запрос не может быть выполнен\",\"fields\":[]}");
    assertThat(body(op().post(BASE + "/typed", "{}").andExpect(status().isMethodNotAllowed())))
        .isEqualTo("{\"code\":\"METHOD_NOT_ALLOWED\",\"message\":\"Запрос не может быть выполнен\",\"fields\":[]}");
    assertThat(body(op().get(BASE + "/conflict"))).startsWith(
        "{\"code\":\"PROFILE_IN_USE\",\"message\":\"Профиль используется\",\"fields\":[],\"devices\":[");
    String ct = op().get(BASE + "/not-found").andReturn().getResponse().getContentType();
    assertThat(ct).startsWith("application/json");
  }

  @Test
  void missingBodyAndMissingParam() throws Exception {
    op().post(BASE + "/body", null).andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("BAD_JSON")).andExpect(jsonPath("$.fields").isArray());
    op().get(BASE + "/typed").andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("BAD_REQUEST")).andExpect(jsonPath("$.fields").isEmpty());
  }

  @Test
  void leakFreeValidation() throws Exception {
    String marker = "zz" + UUID.randomUUID().toString().replace("-", "");
    String b = body(op().get(BASE + "/page?sort=" + marker).andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
        .andExpect(jsonPath("$.message").value("Проверьте параметры запроса"))
        .andExpect(jsonPath("$.fields[0].message").value("Недопустимая сортировка")));
    assertThat(b).doesNotContain(marker, "created_at");
    b = body(op().get(BASE + "/page?sort=createdAt,bad").andExpect(status().isBadRequest()));
    assertThat(b).doesNotContain("created_at", "bad");
    b = body(op().get(BASE + "/page?page=-1&size=0&sort=foo").andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.fields.length()").value(3)));
    assertThat(b).doesNotContain("foo");
  }

  @Test
  void strictParsingOverHttp() throws Exception {
    for (String q : new String[] {"page=%2B5", "page=%205", "size=%2B5", "size=", "page=", "sort=", "sort=name,",
        "sort=,asc", "sort=Name", "sort=name,desc,", "sort=name%20", "size=0500x", "page=99999999999",
        "sort=name&sort=name", "page=1&page=2"}) {
      op().get(BASE + "/page?" + q).andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }
    op().get(BASE + "/page?sort=name,asc").andExpect(status().isOk());
  }

  @Test
  void pageBeyondLastIsOk() throws Exception {
    op().get(BASE + "/page?page=1000&size=10").andExpect(status().isOk())
        .andExpect(jsonPath("$.page").value(1000)).andExpect(jsonPath("$.size").value(10))
        .andExpect(jsonPath("$.total").value(0));
  }
}
