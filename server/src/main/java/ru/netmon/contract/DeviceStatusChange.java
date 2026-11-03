package ru.netmon.contract;

import java.math.BigDecimal; import java.net.InetAddress; import java.time.Instant; import java.util.*; import java.util.stream.Stream;

public record DeviceStatusChange(long deviceId, DeviceStatus oldStatus, DeviceStatus newStatus, Instant ts, String cause) {}
