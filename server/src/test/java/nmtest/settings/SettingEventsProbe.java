package nmtest.settings;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import ru.netmon.contract.events.SettingChanged;

@Component
public class SettingEventsProbe {
  public final List<SettingChanged> events = new CopyOnWriteArrayList<>();
  public volatile boolean fail;

  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  void on(SettingChanged e) {
    events.add(e);
    if (fail) throw new IllegalStateException("probe failure");
  }
}
