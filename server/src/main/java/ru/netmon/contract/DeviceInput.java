package ru.netmon.contract;

import java.math.BigDecimal; import java.net.InetAddress; import java.time.Instant; import java.util.*; import java.util.stream.Stream;

public record DeviceInput(String name, Long typeId, Long groupId, String ip, BigDecimal lat, BigDecimal lon,
    String address, Long profileId, String note, Boolean monitoringEnabled, Long version) {}
