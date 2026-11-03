package ru.netmon.contract;

import java.math.BigDecimal; import java.net.InetAddress; import java.time.Instant; import java.util.*; import java.util.stream.Stream;

public enum CheckError { TIMEOUT, REFUSED, UNREACHABLE, AUTH, NO_SUCH_OBJECT, EMPTY, PARSE, MISMATCH, INTERNAL }
