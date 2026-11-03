package nmtest.common;

import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.Map;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.request.async.AsyncRequestNotUsableException;
import org.springframework.web.context.request.async.AsyncRequestTimeoutException;
import org.springframework.web.server.ResponseStatusException;
import ru.netmon.common.web.ApiException;
import ru.netmon.common.web.PageQuery;
import ru.netmon.common.web.PageResponse;

/** Тестовый контроллер ошибок и постраничности; подключается только через @Import к ApiErrorsTest. */
@RestController
@RequestMapping("/api/test-errors")
public class ErrorsTestController {

  public record Body(@NotBlank(message = "Название обязательно") String name,
      @Min(value = 1, message = "Не меньше 1") int n) {}

  public record Item(@Min(value = 1, message = "Не меньше 1") int port) {}

  public record Mixed(@NotBlank(message = "Название обязательно") String name, @Valid List<Item> checks) {}

  @PreAuthorize("permitAll()")
  @PostMapping("/mixed/{id}")
  public Mixed mixed(@PathVariable @Min(value = 1, message = "Не меньше 1") long id, @Valid @RequestBody Mixed m) {
    return m;
  }

  @PreAuthorize("permitAll()")
  @GetMapping("/denied-wrapped")
  public void deniedWrapped() { throw new RuntimeException(new AccessDeniedException("denied")); }

  @PreAuthorize("permitAll()")
  @GetMapping("/status")
  public void status(@RequestParam int s) { throw new ResponseStatusException(HttpStatusCode.valueOf(s)); }

  @PreAuthorize("permitAll()")
  @GetMapping("/fatal")
  public void fatal() { throw new StackOverflowError("fatal"); }

  @PreAuthorize("permitAll()")
  @GetMapping("/gone")
  public void gone() throws AsyncRequestNotUsableException { throw new AsyncRequestNotUsableException("gone"); }

  @PreAuthorize("permitAll()")
  @GetMapping("/async-timeout")
  public void asyncTimeout() throws AsyncRequestTimeoutException { throw new AsyncRequestTimeoutException(); }

  @PreAuthorize("permitAll()")
  @GetMapping("/committed")
  public void committed(@RequestParam String m, HttpServletResponse r) throws IOException {
    startStream(r);
    throw new IllegalStateException(m);
  }

  @PreAuthorize("permitAll()")
  @GetMapping("/committed-abort")
  public void committedAbort(HttpServletResponse r) throws IOException {
    startStream(r);
    throw new UncheckedIOException(new IOException("abort"));
  }

  private static void startStream(HttpServletResponse r) throws IOException {
    r.setContentType("text/event-stream");
    r.getWriter().write("data: x\n\n");
    r.flushBuffer();
  }

  @PreAuthorize("permitAll()")
  @PostMapping("/body")
  public Body body(@Valid @RequestBody Body b) { return b; }

  @PreAuthorize("permitAll()")
  @GetMapping("/typed")
  public long typed(@RequestParam long n) { return n; }

  @PreAuthorize("permitAll()")
  @GetMapping("/checked")
  public int checked(@RequestParam @Min(value = 1, message = "Не меньше 1") int n) { return n; }

  @PreAuthorize("permitAll()")
  @GetMapping("/not-found")
  public void notFound() { throw ApiException.notFound(); }

  @PreAuthorize("permitAll()")
  @GetMapping("/conflict")
  public void conflict() {
    throw new ApiException(HttpStatus.CONFLICT, "PROFILE_IN_USE", "Профиль используется", List.of(),
        Map.of("devices", List.of(Map.of("id", 1, "name", "d"))));
  }

  @PreAuthorize("permitAll()")
  @GetMapping("/db")
  public void db() {
    throw new DataIntegrityViolationException("insert into marker violates constraint ux_marker_db");
  }

  @PreAuthorize("permitAll()")
  @GetMapping("/boom")
  public void boom(@RequestParam String m) { throw new IllegalStateException(m); }

  @PreAuthorize("permitAll()")
  @GetMapping("/denied")
  public void denied() { throw new AccessDeniedException("denied"); }

  @PreAuthorize("permitAll()")
  @GetMapping("/page")
  public PageResponse<String> page(@RequestParam(required = false) String page,
      @RequestParam(required = false) String size, @RequestParam(required = false) String sort) {
    PageQuery q = PageQuery.parse(page, size, sort, Map.of("name", "name", "createdAt", "created_at"), "name,asc");
    return PageResponse.of(List.of(q.orderBy("id")), q, 0);
  }
}
