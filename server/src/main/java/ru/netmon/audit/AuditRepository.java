package ru.netmon.audit;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import ru.netmon.common.web.PageQuery;
import ru.netmon.contract.Actor;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@Repository
class AuditRepository {

  record Filter(Long userId, String objectType, Instant from, Instant to) {}

  private final JdbcClient jdbc;
  private final JsonMapper json;

  AuditRepository(JdbcClient jdbc, JsonMapper json) {
    this.jdbc = jdbc;
    this.json = json;
  }

  long insert(Instant ts, Actor a, String action, String objectType, String objectId, String oldJson,
      String newJson) {
    return jdbc.sql("insert into audit_log(ts, user_id, user_login, action, object_type, object_id, old_value,"
        + " new_value) values (?, ?, ?, ?, ?, ?, ?::jsonb, ?::jsonb) returning id")
        .params(OffsetDateTime.ofInstant(ts, ZoneOffset.UTC), a.userId(), a.login(), action, objectType, objectId,
            oldJson, newJson)
        .query(Long.class).single();
  }

  private static String where(Filter f, List<Object> args) {
    StringBuilder w = new StringBuilder(" where true");
    if (f.userId() != null) {
      w.append(" and user_id = ?");
      args.add(f.userId());
    }
    if (f.objectType() != null) {
      w.append(" and object_type = ?");
      args.add(f.objectType());
    }
    if (f.from() != null) {
      w.append(" and ts >= ?");
      args.add(OffsetDateTime.ofInstant(f.from(), ZoneOffset.UTC));
    }
    if (f.to() != null) {
      w.append(" and ts < ?");
      args.add(OffsetDateTime.ofInstant(f.to(), ZoneOffset.UTC));
    }
    return w.toString();
  }

  long count(Filter f) {
    List<Object> args = new ArrayList<>();
    String w = where(f, args);
    return jdbc.sql("select count(*) from audit_log" + w).params(args).query(Long.class).single();
  }

  List<AuditEntry> page(Filter f, PageQuery q) {
    List<Object> args = new ArrayList<>();
    String w = where(f, args);
    args.add(q.size());
    args.add(q.offset());
    return jdbc.sql("select id, ts, user_id, user_login, action, object_type, object_id, old_value::text o,"
        + " new_value::text n from audit_log" + w + " order by " + q.orderBy("id") + " limit ? offset ?")
        .params(args)
        .query((rs, i) -> new AuditEntry(rs.getLong("id"), rs.getObject("ts", OffsetDateTime.class).toInstant(),
            (Long) rs.getObject("user_id"), rs.getString("user_login"), rs.getString("action"),
            rs.getString("object_type"), rs.getString("object_id"), tree(rs.getString("o")),
            tree(rs.getString("n"))))
        .list();
  }

  private JsonNode tree(String s) {
    return s == null ? null : json.readTree(s);
  }
}
