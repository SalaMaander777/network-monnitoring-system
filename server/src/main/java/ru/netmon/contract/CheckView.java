package ru.netmon.contract;

import java.math.BigDecimal; import java.net.InetAddress; import java.time.Instant; import java.util.*; import java.util.stream.Stream;

public record CheckView(long deviceId, long checkId, Instant lastTs, Boolean lastSuccess, BigDecimal lastValueNum,
    String lastValueText, CheckError lastError, int consecutiveFailures, int consecutiveSuccesses, CheckHealth health,
    ThresholdLevel thresholdLevel, int thresholdOkStreak, boolean required, boolean primary, boolean enabled, int intervalSec) {}
