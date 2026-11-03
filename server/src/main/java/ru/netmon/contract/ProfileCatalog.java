package ru.netmon.contract;

import java.math.BigDecimal; import java.net.InetAddress; import java.time.Instant; import java.util.*; import java.util.stream.Stream;

public interface ProfileCatalog { Optional<ProfileView> find(long id); List<CheckDefinition> checks(long profileId); }
