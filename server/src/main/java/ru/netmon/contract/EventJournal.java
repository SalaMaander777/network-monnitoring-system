package ru.netmon.contract;

import java.math.BigDecimal; import java.net.InetAddress; import java.time.Instant; import java.util.*; import java.util.stream.Stream;

public interface EventJournal { void record(EventType t, Long deviceId, InetAddress sourceIp, String description, Map<String,Object> details, Instant ts); }
