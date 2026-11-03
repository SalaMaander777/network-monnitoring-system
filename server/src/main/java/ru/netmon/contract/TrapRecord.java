package ru.netmon.contract;

import java.math.BigDecimal; import java.net.InetAddress; import java.time.Instant; import java.util.*; import java.util.stream.Stream;

public record TrapRecord(InetAddress source, String trapOid, Map<String,String> varbinds, Instant ts, String snmpVersion) {}
