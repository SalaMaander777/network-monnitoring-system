package ru.netmon.stream;
import java.util.Map; import java.util.concurrent.atomic.AtomicLong;
import org.springframework.stereotype.Component; import ru.netmon.contract.StreamPublisher;
@Component
public class SeqStreamPublisher implements StreamPublisher {
  private final AtomicLong seq = new AtomicLong();
  @Override public void publish(String event, Map<String,Object> data) { seq.incrementAndGet(); }
  @Override public long currentSeq() { return seq.get(); }
}
