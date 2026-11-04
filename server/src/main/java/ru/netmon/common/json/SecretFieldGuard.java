package ru.netmon.common.json;

import java.util.Locale;
import java.util.Set;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

public final class SecretFieldGuard {
  private static final Set<String> NAMES = Set.of("password", "oldpassword", "newpassword", "passwordhash",
      "password_hash", "community", "authpassword", "privpassword", "ciphertext");

  private SecretFieldGuard() {}

  public static boolean isSecretName(String name) {
    return NAMES.contains(name.toLowerCase(Locale.ROOT));
  }

  public static JsonNode strip(JsonNode node) {
    if (node == null) return null;
    JsonNode copy = node.deepCopy();
    clean(copy);
    return copy;
  }

  private static void clean(JsonNode n) {
    if (n instanceof ObjectNode o) {
      o.properties().removeIf(e -> isSecretName(e.getKey()));
      for (JsonNode c : o.values()) clean(c);
    } else if (n instanceof ArrayNode a) {
      for (JsonNode c : a.values()) clean(c);
    }
  }
}
