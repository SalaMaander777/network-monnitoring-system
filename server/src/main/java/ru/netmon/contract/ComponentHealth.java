package ru.netmon.contract;

import java.math.BigDecimal; import java.net.InetAddress; import java.time.Instant; import java.util.*; import java.util.stream.Stream;

public record ComponentHealth(String name, State state, String message, Map<String,String> details) {
  public enum State { UP, DEGRADED, DOWN } }
