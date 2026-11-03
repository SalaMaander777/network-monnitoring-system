package ru.netmon.contract;

import java.math.BigDecimal; import java.net.InetAddress; import java.time.Instant; import java.util.*; import java.util.stream.Stream;

public record CheckTransition(long deviceId, CheckDefinition check, CheckHealth oldHealth, CheckHealth newHealth,
    ThresholdLevel oldLevel, ThresholdLevel newLevel, CheckOutcome outcome, Instant ts) {}
