package ru.netmon.common;
import java.time.Clock; import org.springframework.context.annotation.*;
@Configuration(proxyBeanMethods = false)
public class ClockConfig { @Bean public Clock clock() { return Clock.systemUTC(); } }
