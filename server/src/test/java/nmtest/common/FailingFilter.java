package nmtest.common;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.web.filter.OncePerRequestFilter;

/** Бросает исключение на /api/test-filter-fail до Spring Security; подключается через extraSources. */
@Order(Ordered.HIGHEST_PRECEDENCE)
public class FailingFilter extends OncePerRequestFilter {

  public static final String PATH = "/api/test-filter-fail";
  public static final String MARKER = "failing-filter-marker-91c2";

  @Override
  protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    if (PATH.equals(request.getRequestURI())) {
      throw new IllegalStateException(MARKER);
    }
    chain.doFilter(request, response);
  }
}
