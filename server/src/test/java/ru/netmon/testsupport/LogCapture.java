package ru.netmon.testsupport;
import ch.qos.logback.classic.Level; import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.*; import ch.qos.logback.core.AppenderBase;
import java.util.LinkedHashMap; import java.util.Map;
import org.junit.jupiter.api.extension.*; import org.slf4j.LoggerFactory;
public final class LogCapture implements BeforeEachCallback, AfterEachCallback {
  private static final StringBuffer BUF = new StringBuffer();
  public static final class Appender extends AppenderBase<ILoggingEvent> { protected void append(ILoggingEvent e) {
      StringBuilder b = new StringBuilder().append(e.getLevel()).append(' ').append(e.getLoggerName()).append(' ').append(e.getFormattedMessage()).append(' ').append(e.getMDCPropertyMap());
      if (e.getThrowableProxy()!=null) b.append(ThrowableProxyUtil.asString(e.getThrowableProxy())); BUF.append(b).append('\n'); } }
  private final Map<String, Level> saved = new LinkedHashMap<>();
  public void beforeEach(ExtensionContext c) { BUF.setLength(0); saved.clear(); }
  public void afterEach(ExtensionContext c) {
    saved.forEach((n, l) -> ((Logger) LoggerFactory.getLogger(n)).setLevel(l));
    saved.clear();
  }
  public String text() { return BUF.toString(); }
  public void level(String name, Level level) {
    Logger lg = (Logger) LoggerFactory.getLogger(name);
    if (!saved.containsKey(name)) saved.put(name, lg.getLevel());
    lg.setLevel(level);
  }
}
