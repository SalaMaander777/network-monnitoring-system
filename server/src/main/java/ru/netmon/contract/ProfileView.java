package ru.netmon.contract;

import java.math.BigDecimal; import java.net.InetAddress; import java.time.Instant; import java.util.*; import java.util.stream.Stream;

public record ProfileView(long id, String name, String description, long version, List<CheckDefinition> checks) {}
