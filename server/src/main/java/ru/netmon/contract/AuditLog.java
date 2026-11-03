package ru.netmon.contract;

import java.math.BigDecimal; import java.net.InetAddress; import java.time.Instant; import java.util.*; import java.util.stream.Stream;

public interface AuditLog { void record(Actor a, String action, String objectType, String objectId, Object oldValue, Object newValue); }
