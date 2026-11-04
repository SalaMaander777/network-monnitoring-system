package ru.netmon.common.json;

import java.util.Map;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.JsonNodeFactory;
import tools.jackson.databind.node.ObjectNode;

public final class NulGuard {
  private NulGuard() {}

  public static String text(String s) {
    return s == null || s.indexOf('\u0000') < 0 ? s : s.replace('\u0000', '�');
  }

  public static JsonNode tree(JsonNode n) {
    if (n == null) return null;
    JsonNodeFactory f = JsonNodeFactory.instance;
    if (n instanceof ObjectNode o) {
      ObjectNode r = f.objectNode();
      for (Map.Entry<String, JsonNode> e : o.properties()) r.set(text(e.getKey()), tree(e.getValue()));
      return r;
    }
    if (n instanceof ArrayNode a) {
      ArrayNode r = f.arrayNode();
      for (JsonNode c : a.values()) r.add(tree(c));
      return r;
    }
    if (n.isString()) return f.stringNode(text(n.stringValue()));
    return n.deepCopy();
  }
}
