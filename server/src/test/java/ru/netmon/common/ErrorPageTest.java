package ru.netmon.common;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;
import nmtest.common.FailingFilter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import ru.netmon.testsupport.IntegrationTest;
import ru.netmon.testsupport.LogCapture;
import ru.netmon.testsupport.RestartableApp;

@IntegrationTest
class ErrorPageTest {

  private static final String INTERNAL =
      "{\"code\":\"INTERNAL_ERROR\",\"message\":\"Внутренняя ошибка сервера\",\"fields\":[]}";

  @Autowired
  MockMvc mvc;

  @RegisterExtension
  LogCapture logs = new LogCapture();

  private MockHttpServletRequestBuilder errorRequest(int status) {
    return get("/error").with(user("u").roles("OPERATOR")).requestAttr("jakarta.servlet.error.status_code", status);
  }

  @Test
  void errorAttributesToApiFormat() throws Exception {
    String marker = "error-page-marker-" + UUID.randomUUID();
    MvcResult r = mvc.perform(errorRequest(500)
        .requestAttr("jakarta.servlet.error.exception", new IllegalStateException(marker))
        .requestAttr("jakarta.servlet.error.request_uri", "/api/some")).andReturn();
    assertThat(r.getResponse().getStatus()).isEqualTo(500);
    assertThat(r.getResponse().getContentAsString(StandardCharsets.UTF_8)).isEqualTo(INTERNAL);
    assertThat(logs.text()).contains(marker);

    MvcResult notFound = mvc.perform(errorRequest(404)).andReturn();
    assertThat(notFound.getResponse().getStatus()).isEqualTo(404);
    assertThat(notFound.getResponse().getContentAsString(StandardCharsets.UTF_8))
        .isEqualTo("{\"code\":\"NOT_FOUND\",\"message\":\"Запрос не может быть выполнен\",\"fields\":[]}");
    MvcResult tooBig = mvc.perform(errorRequest(413)).andReturn();
    assertThat(tooBig.getResponse().getStatus()).isEqualTo(413);
    assertThat(tooBig.getResponse().getContentAsString(StandardCharsets.UTF_8)).isEqualTo(
        "{\"code\":\"PAYLOAD_TOO_LARGE\",\"message\":\"Запрос не может быть выполнен\",\"fields\":[]}");
  }

  @Test
  void filterExceptionGoesToErrorPage() throws Exception {
    try (RestartableApp app = new RestartableApp(Map.of(), FailingFilter.class).start()) {
      URI uri = URI.create("http://127.0.0.1:" + app.port() + FailingFilter.PATH);
      HttpClient client = HttpClient.newHttpClient();
      HttpResponse<String> getResponse = client.send(HttpRequest.newBuilder(uri).build(),
          HttpResponse.BodyHandlers.ofString());
      HttpResponse<String> postResponse = client.send(HttpRequest.newBuilder(uri)
          .POST(HttpRequest.BodyPublishers.ofString("a=b")).header("Content-Type",
              "application/x-www-form-urlencoded").build(), HttpResponse.BodyHandlers.ofString());
      for (HttpResponse<String> r : java.util.List.of(getResponse, postResponse)) {
        assertThat(r.statusCode()).isEqualTo(500);
        assertThat(r.headers().firstValue("content-type")).hasValueSatisfying(v -> assertThat(v)
            .startsWith("application/json"));
        assertThat(r.body()).isEqualTo(INTERNAL);
        assertThat(r.body()).doesNotContain(FailingFilter.MARKER, "timestamp", "trace", "exception");
      }
      assertThat(logs.text()).contains(FailingFilter.MARKER);
    }
  }
}
