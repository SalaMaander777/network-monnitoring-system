package ru.netmon.common;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import ru.netmon.common.json.SecretFieldGuard;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

class SecretFieldGuardTest {

  private final JsonMapper json = JsonMapper.builder().build();

  @Test
  void removesAtAnyDepth() {
    JsonNode in = json.readTree("{\"a\":{\"community\":\"S1\",\"list\":[{\"authPassword\":\"S2\",\"x\":1},"
        + "[{\"privPassword\":\"S3\"}]]},\"Password\":\"S4\",\"PASSWORD_HASH\":\"S5\",\"ciphertext\":\"S6\","
        + "\"oldPassword\":\"S7\",\"newPassword\":\"S8\",\"passwordHash\":\"S9\"}");
    assertThat(json.writeValueAsString(SecretFieldGuard.strip(in))).isEqualTo("{\"a\":{\"list\":[{\"x\":1},[{}]]}}");
  }

  @Test
  void keepsOtherFieldsAndValues() {
    JsonNode in = json.readTree("{\"note\":\"password\",\"passwordPolicy\":\"community\",\"user\":\"u\"}");
    assertThat(SecretFieldGuard.strip(in)).isEqualTo(in);
  }

  @Test
  void doesNotMutateInput() {
    JsonNode in = json.readTree("{\"a\":{\"community\":\"S1\",\"l\":[{\"password\":\"S2\"}]},\"password\":\"S3\"}");
    JsonNode before = in.deepCopy();
    SecretFieldGuard.strip(in);
    assertThat(in).isEqualTo(before);
  }

  @Test
  void nullAndScalars() {
    assertThat(SecretFieldGuard.strip(null)).isNull();
    JsonNode s = json.readTree("\"text\"");
    JsonNode n = json.readTree("42");
    assertThat(SecretFieldGuard.strip(s)).isEqualTo(s);
    assertThat(SecretFieldGuard.strip(n)).isEqualTo(n);
  }
}
