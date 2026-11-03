package ru.netmon.contract;

import java.math.BigDecimal; import java.net.InetAddress; import java.time.Instant; import java.util.*; import java.util.stream.Stream;

public record CheckDefinition(long id, long profileId, String code, String name, CheckKind kind,
    Map<String,Object> params, int intervalSec, int timeoutMs,
    BigDecimal warn, BigDecimal crit, BigDecimal hysteresis, ThresholdDirection direction,
    int failCount, int successCount, boolean required, boolean primary, boolean enabled, boolean unconfirmed) {}
