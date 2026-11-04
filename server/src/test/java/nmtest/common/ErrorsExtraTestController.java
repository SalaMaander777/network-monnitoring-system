package nmtest.common;

import jakarta.validation.constraints.Min;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Дополнительные тестовые методы для ApiErrorsTest (заголовки ошибок, кросс-параметрическая проверка). */
@RestController
@RequestMapping("/api/test-errors-extra")
public class ErrorsExtraTestController {

  public record Payload(String name) {}

  @PreAuthorize("permitAll()")
  @GetMapping("/get-only")
  public String getOnly() {
    return "x";
  }

  @PreAuthorize("permitAll()")
  @PostMapping(path = "/json-only", consumes = MediaType.APPLICATION_JSON_VALUE)
  public Payload jsonOnly(@RequestBody Payload payload) {
    return payload;
  }

  @PreAuthorize("permitAll()")
  @GetMapping("/range")
  @OrderedRange(message = "Начало диапазона больше конца")
  public String range(@RequestParam @Min(value = 0, message = "Не меньше 0") int from, @RequestParam int to) {
    return from + "-" + to;
  }
}
