package ru.netmon.contract;

import java.math.BigDecimal; import java.net.InetAddress; import java.time.Instant; import java.util.*; import java.util.stream.Stream;

public record TrapMatch(long deviceId, long ruleId, String trapOid, TrapAction action, AlarmLevel level,
    String pairKey, String correlationValue, String description, Instant ts) {}
