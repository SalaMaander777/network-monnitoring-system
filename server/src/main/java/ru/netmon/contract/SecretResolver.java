package ru.netmon.contract;

import java.math.BigDecimal; import java.net.InetAddress; import java.time.Instant; import java.util.*; import java.util.stream.Stream;

public interface SecretResolver { Optional<SnmpCredentials> forDevice(long deviceId, Long groupId); }
