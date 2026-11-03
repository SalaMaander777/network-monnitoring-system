package ru.netmon.testsupport;
import java.util.*; import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.web.server.context.WebServerApplicationContext;
import org.springframework.context.ConfigurableApplicationContext; import ru.netmon.NetMonitoringApplication;
public final class RestartableApp implements AutoCloseable {
  private final Map<String,Object> props = new HashMap<>(); private final Class<?>[] extra; private ConfigurableApplicationContext ctx;
  public RestartableApp(Map<String,String> extraProps, Class<?>... extraSources) { props.putAll(TestPostgres.properties()); props.putAll(extraProps); props.put("server.port","0"); extra = extraSources; }
  public RestartableApp start() { if (ctx != null) throw new IllegalStateException("RestartableApp уже запущен: используйте restart()"); List<Class<?>> s = new ArrayList<>(List.of(NetMonitoringApplication.class)); s.addAll(List.of(extra));
    ctx = new SpringApplicationBuilder(s.toArray(Class[]::new)).profiles("test").run(props.entrySet().stream().map(e -> "--" + e.getKey() + "=" + e.getValue()).toArray(String[]::new)); return this; }
  public int port() { return ((WebServerApplicationContext) ctx).getWebServer().getPort(); }
  public ConfigurableApplicationContext context() { return ctx; }
  public void stop() { if (ctx != null) { ctx.close(); ctx = null; } }
  public RestartableApp restart() { stop(); return start(); }
  public void close() { stop(); }
}
