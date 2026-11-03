package ru.netmon.contract;

import java.math.BigDecimal; import java.net.InetAddress; import java.time.Instant; import java.util.*; import java.util.stream.Stream;

public enum EventType { STATUS_CHANGE, TRAP, TRAP_UNKNOWN_SOURCE, USER_ACTION }
