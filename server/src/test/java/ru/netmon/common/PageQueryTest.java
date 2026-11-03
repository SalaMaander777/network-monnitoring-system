package ru.netmon.common;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import ru.netmon.common.web.ApiException;
import ru.netmon.common.web.FieldError;
import ru.netmon.common.web.PageQuery;

class PageQueryTest {

  private static final Map<String, String> ALLOWED = Map.of("name", "name", "createdAt", "created_at");

  private static PageQuery parse(String page, String size, String sort) {
    return PageQuery.parse(page, size, sort, ALLOWED, "name,asc");
  }

  @Test
  void defaults() {
    PageQuery q = parse(null, null, null);
    assertThat(q.page()).isZero();
    assertThat(q.size()).isEqualTo(50);
    assertThat(q.sortColumn()).isEqualTo("name");
    assertThat(q.descending()).isFalse();
    assertThat(q.orderBy("id")).isEqualTo("name asc, id asc");
    assertThat(parse("2", "500", "createdAt,desc").orderBy("id")).isEqualTo("created_at desc, id desc");
    assertThat(q.orderBy("name")).isEqualTo("name asc");
  }

  @Test
  void noPublicConstructor() {
    assertThat(PageQuery.class.getConstructors()).isEmpty();
    assertThat(java.lang.reflect.Modifier.isFinal(PageQuery.class.getModifiers())).isTrue();
    assertThat(PageQuery.class.isRecord()).isFalse();
  }

  @Test
  void offsetDoesNotOverflow() {
    assertThat(parse("2147483647", "500", null).offset()).isEqualTo(2147483647L * 500);
  }

  @Test
  void collectsAllViolations() {
    assertThatThrownBy(() -> parse("-1", "0", "foo")).isInstanceOfSatisfying(ApiException.class, e -> {
      assertThat(e.status()).isEqualTo(HttpStatus.BAD_REQUEST);
      assertThat(e.code()).isEqualTo("VALIDATION_FAILED");
      assertThat(e.getMessage()).isEqualTo("Проверьте параметры запроса");
      assertThat(e.fields()).containsExactly(
          new FieldError("page", "Номер страницы: целое число от 0"),
          new FieldError("size", "Размер страницы: целое число от 1 до 500"),
          new FieldError("sort", "Недопустимая сортировка"));
    });
  }

  @Test
  void signAndSpacesAreInvalid() {
    assertThatThrownBy(() -> parse("+5", null, null)).isInstanceOf(ApiException.class);
    assertThatThrownBy(() -> parse(" 5", null, null)).isInstanceOf(ApiException.class);
    assertThatThrownBy(() -> parse(null, "5 ", null)).isInstanceOf(ApiException.class);
    assertThatThrownBy(() -> parse("", null, null)).isInstanceOf(ApiException.class);
    assertThatThrownBy(() -> parse("2147483648", null, null)).isInstanceOf(ApiException.class);
    assertThatThrownBy(() -> parse("00000000000", null, null)).isInstanceOf(ApiException.class);
  }

  @Test
  void invalidDefaultSortIsCodeError() {
    assertThatThrownBy(() -> PageQuery.parse(null, null, null, ALLOWED, "foo"))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> PageQuery.parse(null, null, "name", ALLOWED, "name,ASC"))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void reservedDetailsKeyIsRejected() {
    for (String key : List.of("code", "message", "fields")) {
      assertThatThrownBy(() -> new ApiException(HttpStatus.CONFLICT, "X", "m", List.of(), Map.of(key, 1)))
          .isInstanceOf(IllegalArgumentException.class);
    }
  }
}
