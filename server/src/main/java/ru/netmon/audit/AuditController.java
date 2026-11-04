package ru.netmon.audit;

import java.time.Instant;
import java.util.Map;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import ru.netmon.common.web.PageQuery;
import ru.netmon.common.web.PageResponse;
import ru.netmon.common.web.PeriodParams;

@RestController
class AuditController {

  private final AuditRepository repository;

  AuditController(AuditRepository repository) {
    this.repository = repository;
  }

  @PreAuthorize("hasRole('ADMIN')")
  @GetMapping("/api/audit")
  PageResponse<AuditEntry> list(@RequestParam(required = false) String page,
      @RequestParam(required = false) String size, @RequestParam(required = false) String sort,
      @RequestParam(required = false) Long userId, @RequestParam(required = false) String objectType,
      @RequestParam(required = false) Instant from, @RequestParam(required = false) Instant to) {
    PageQuery q = PageQuery.parse(page, size, sort, Map.of("ts", "ts"), "ts,desc");
    PeriodParams.check(from, to);
    AuditRepository.Filter f = new AuditRepository.Filter(userId, objectType, from, to);
    long total = repository.count(f);
    return PageResponse.of(repository.page(f, q), q, total);
  }
}
