package ru.netmon.contract;

import java.math.BigDecimal; import java.net.InetAddress; import java.time.Instant; import java.util.*; import java.util.stream.Stream;

public record DeviceFilter(String q, Set<Long> typeIds, Set<Long> groupIds, Set<DeviceStatus> statuses, Boolean monitoring) {}
