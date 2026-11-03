package ru.netmon.contract;

import java.math.BigDecimal; import java.net.InetAddress; import java.time.Instant; import java.util.*; import java.util.stream.Stream;

public record DeviceView(long id, String name, long typeId, String typeName, Long groupId, String groupName,
    InetAddress ip, BigDecimal lat, BigDecimal lon, String address, Long profileId, String profileName, String note,
    boolean monitoringEnabled, DeviceStatus status, Instant statusChangedAt, Instant createdAt, Instant updatedAt, long version) {}
