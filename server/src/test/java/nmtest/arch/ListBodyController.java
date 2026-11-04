package nmtest.arch;

import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/** Заведомо нарушающий §2.2 п. 5 контроллер для самопроверки правила; нигде не подключается. */
@RestController
public class ListBodyController {

  @PreAuthorize("permitAll()")
  @PostMapping("/api/test-arch/list")
  public int list(@RequestBody List<String> items) {
    return items.size();
  }
}
