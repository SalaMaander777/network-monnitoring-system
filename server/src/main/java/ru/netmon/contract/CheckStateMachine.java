package ru.netmon.contract;

import java.math.BigDecimal; import java.net.InetAddress; import java.time.Instant; import java.util.*; import java.util.stream.Stream;

public interface CheckStateMachine { CheckView apply(CheckView current, CheckDefinition check, CheckResult result, ThresholdLevel target); }
