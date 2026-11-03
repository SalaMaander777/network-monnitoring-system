package ru.netmon.contract;

import java.math.BigDecimal; import java.net.InetAddress; import java.time.Instant; import java.util.*; import java.util.stream.Stream;

public sealed interface SnmpCredentials {
  record V2c(String community) implements SnmpCredentials { @Override public String toString() { return "V2c[community=***]"; } }
  record V3(String user, String authProtocol, String authPassword, String privProtocol, String privPassword)
      implements SnmpCredentials { @Override public String toString() { return "V3[user=***, authProtocol=***, authPassword=***, privProtocol=***, privPassword=***]"; } }
}
