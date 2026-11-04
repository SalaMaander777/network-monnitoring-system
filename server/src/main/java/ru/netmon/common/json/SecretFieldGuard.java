package ru.netmon.common.json;

import java.util.List;
import java.util.Locale;
import java.util.Set;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

public final class SecretFieldGuard {
  private static final List<String> PARTS = List.of("password", "passphrase", "secret", "token", "community",
      "ciphertext", "privatekey", "apikey");
  private static final Set<String> EXACT = Set.of("authkey", "privkey", "masterkey");

  private SecretFieldGuard() {}

  public static boolean isSecretName(String name) {
    String n = name.toLowerCase(Locale.ROOT).replace("_", "").replace("-", "");
    if (EXACT.contains(n)) return true;
    for (String p : PARTS) if (n.contains(p)) return true;
    return false;
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
