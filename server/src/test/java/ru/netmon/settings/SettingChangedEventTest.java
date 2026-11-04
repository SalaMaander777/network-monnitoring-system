package ru.netmon.settings;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import nmtest.settings.SettingEventsProbe;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import ru.netmon.contract.Actor;
import ru.netmon.contract.Role;
import ru.netmon.contract.Settings;
import ru.netmon.contract.events.SettingChanged;
import ru.netmon.testsupport.Api;
import ru.netmon.testsupport.IntegrationTest;
import ru.netmon.testsupport.TestData;
import ru.netmon.testsupport.TestDb;

@IntegrationTest
@Import(SettingEventsProbe.class)
class SettingChangedEventTest {
  @Autowired SettingEventsProbe probe;
  @Autowired SettingsService service;
  @Autowired Settings settings;
  @Autowired PlatformTransactionManager tm;
  @Autowired TestData data;
  @Autowired TestDb db;
  @Autowired Api api;

  @BeforeEach
  void reset() { probe.events.clear(); probe.fail = false; }

  @AfterEach
  void restore() { probe.fail = false; db.restoreSettings(); }

  @Test
  void publishedAfterCommit() throws Exception {
    api.as(Role.ADMIN).put("/api/settings/traps.udp_port", "{\"value\":\"10162\"}").andExpect(status().isOk());
    assertThat(probe.events).containsExactly(new SettingChanged("traps.udp_port", "10162"));
  }

  @Test
  void notPublishedOnRollback() {
    Actor a = new Actor(data.user(Role.ADMIN), "adm");
    new TransactionTemplate(tm).executeWithoutResult(s -> {
      service.update("traps.udp_port", "10162", a);
      assertThat(probe.events).isEmpty();
      s.setRollbackOnly();
    });
    assertThat(probe.events).isEmpty();
  }

  @Test
  void notPublishedForSameValue() throws Exception {
    api.as(Role.ADMIN).put("/api/settings/traps.udp_port", "{\"value\":\"162\"}").andExpect(status().isOk());
    assertThat(probe.events).isEmpty();
  }

  @Test
  void listenerFailureDoesNotFailRequest() throws Exception {
    probe.fail = true;
    var r = api.as(Role.ADMIN).put("/api/settings/traps.udp_port", "{\"value\":\"10163\"}").andExpect(status().isOk())
        .andReturn().getResponse();
    assertThat(r.getContentAsString(java.nio.charset.StandardCharsets.UTF_8)).isEqualTo("{\"key\":\"traps.udp_port\",\"value\":\"10163\",\"min\":1,\"max\":65535}");
    assertThat(settings.get("traps.udp_port")).isEqualTo("10163");
  }
}
