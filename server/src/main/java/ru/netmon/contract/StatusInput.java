package ru.netmon.contract;

import java.math.BigDecimal; import java.net.InetAddress; import java.time.Instant; import java.util.*; import java.util.stream.Stream;

public record StatusInput(boolean monitoringEnabled, List<CheckView> checks, boolean activeTrapAlarm, Instant now, Instant serverStart) {}
