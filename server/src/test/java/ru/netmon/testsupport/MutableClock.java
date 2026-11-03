package ru.netmon.testsupport;
import java.time.*; import java.time.temporal.ChronoUnit;
import org.springframework.boot.test.context.TestConfiguration; import org.springframework.context.annotation.*;
public final class MutableClock extends Clock {
  private volatile Instant now = start();
  private static Instant start() { return Instant.now().truncatedTo(ChronoUnit.MICROS); }
  public synchronized void reset() { now = start(); }
  public Instant instant() { return now; }
  public ZoneId getZone() { return ZoneOffset.UTC; }
  public Clock withZone(ZoneId z) { return ZoneOffset.UTC.equals(z.normalized()) ? this : new Zoned(this, z); }
  private static final class Zoned extends Clock {
    private final MutableClock base; private final ZoneId zone;
    private Zoned(MutableClock base, ZoneId zone) { this.base = base; this.zone = java.util.Objects.requireNonNull(zone); }
    public Instant instant() { return base.instant(); }
    public ZoneId getZone() { return zone; }
    public Clock withZone(ZoneId z) { return base.withZone(z); }
  }
  public synchronized void set(Instant i) { now = java.util.Objects.requireNonNull(i); }
  public synchronized void advance(Duration d) { now = now.plus(d); }
  @TestConfiguration(proxyBeanMethods = false)
  public static class Config { @Bean @Primary public MutableClock mutableClock() { return new MutableClock(); } }
}
