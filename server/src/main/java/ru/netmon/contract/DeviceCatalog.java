package ru.netmon.contract;

import java.math.BigDecimal; import java.net.InetAddress; import java.time.Instant; import java.util.*; import java.util.stream.Stream;

public interface DeviceCatalog { Optional<DeviceView> find(long id); Optional<DeviceView> findActiveByIp(InetAddress ip); Stream<DeviceView> streamActive(DeviceFilter f); DeviceView create(DeviceInput i, Actor a); }
