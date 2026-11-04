package ru.netmon.testsupport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;
import ru.netmon.contract.Role;

@IntegrationTest
class ApiSelfTest {

  @Autowired
  Api api;

  @Autowired
  MockMvc mvc;

  @Test
  void mutatingRequestCarriesRealCsrfToken() throws Exception {
    api.as(Role.OPERATOR).post("/api/no-such-resource", "{}").andExpect(status().isNotFound());
    api.as(Role.ADMIN).delete("/api/no-such-resource").andExpect(status().isNotFound());
  }

  @Test
  void sharedContextCsrfIntact() throws Exception {
    api.as(Role.OPERATOR).post("/api/no-such-resource", "{}");
    assertThat(mvc.perform(get("/api/auth/csrf")).andExpect(status().isNoContent()).andReturn().getResponse()
        .getCookie("XSRF-TOKEN")).isNotNull();
  }
}
