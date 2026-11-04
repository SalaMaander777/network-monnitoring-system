package ru.netmon.testsupport;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;

import jakarta.servlet.http.Cookie;
import java.util.Arrays;
import java.util.Set;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import ru.netmon.common.security.CurrentUser;
import ru.netmon.contract.Role;

/**
 * Запросы к API от имени роли (architecture.md §12.2): principal {@link CurrentUser}; изменяющий запрос несёт
 * настоящий токен CSRF (cookie XSRF-TOKEN от GET /api/auth/csrf и заголовок X-XSRF-TOKEN). Помощник не меняет
 * состояние общего контекста: SecurityMockMvcRequestPostProcessors.csrf() не используется, потому что он заменяет
 * CsrfTokenRepository в CsrfFilter общего контекста.
 * Строка app_user с логином test-operator или test-admin создаётся при необходимости (после TestDb.clean() её нет).
 */
public class Api {

    private final MockMvc mvc;

    private final JdbcClient jdbc;

    public Api(MockMvc mvc, JdbcClient jdbc) {
        this.mvc = mvc;
        this.jdbc = jdbc;
    }

    private UsernamePasswordAuthenticationToken token(Role role) {
        String login = "test-" + role.name().toLowerCase();
        jdbc.sql("insert into app_user(login, password_hash, role) values (?, 'h', ?) "
                + "on conflict ((lower(login))) do nothing").params(login, role.name()).update();
        long id = jdbc.sql("select id from app_user where lower(login) = lower(?)").param(login)
                .query(Long.class).single();
        CurrentUser principal = new CurrentUser(id, login, role, true, null);
        return UsernamePasswordAuthenticationToken.authenticated(principal, null, principal.getAuthorities());
    }

    private static final Set<HttpMethod> SAFE = Set.of(HttpMethod.GET, HttpMethod.HEAD, HttpMethod.OPTIONS,
            HttpMethod.TRACE);

    /**
     * Настоящий токен CSRF: cookie XSRF-TOKEN из ответа GET /api/auth/csrf и тот же токен в заголовке X-XSRF-TOKEN.
     * Запрос за токеном выполняется при вызове метода, до запроса, к которому токен прикладывается.
     */
    public RequestPostProcessor csrfToken() throws Exception {
        Cookie c = mvc.perform(MockMvcRequestBuilders.get("/api/auth/csrf")).andReturn().getResponse()
                .getCookie("XSRF-TOKEN");
        if (c == null) {
            throw new IllegalStateException("GET /api/auth/csrf не выдал cookie XSRF-TOKEN");
        }
        return request -> {
            Cookie[] old = request.getCookies();
            Cookie[] all = old == null ? new Cookie[1] : Arrays.copyOf(old, old.length + 1);
            all[all.length - 1] = c;
            request.setCookies(all);
            request.addHeader("X-XSRF-TOKEN", c.getValue());
            return request;
        };
    }

    public As as(Role role) {
        return new As(role);
    }

    public final class As {
        private final Role role;

        private As(Role role) {
            this.role = role;
        }

        public ResultActions get(String url, Object... uriVars) throws Exception {
            return send(HttpMethod.GET, url, null, uriVars);
        }

        public ResultActions post(String url, String jsonBody, Object... uriVars) throws Exception {
            return send(HttpMethod.POST, url, jsonBody, uriVars);
        }

        public ResultActions put(String url, String jsonBody, Object... uriVars) throws Exception {
            return send(HttpMethod.PUT, url, jsonBody, uriVars);
        }

        public ResultActions patch(String url, String jsonBody, Object... uriVars) throws Exception {
            return send(HttpMethod.PATCH, url, jsonBody, uriVars);
        }

        public ResultActions delete(String url, Object... uriVars) throws Exception {
            return send(HttpMethod.DELETE, url, null, uriVars);
        }

        private ResultActions send(HttpMethod method, String url, String body, Object... uriVars) throws Exception {
            MockHttpServletRequestBuilder b = MockMvcRequestBuilders.request(method, url, uriVars)
                    .with(authentication(token(role)));
            if (!SAFE.contains(method)) {
                b.with(csrfToken());
            }
            if (body != null) {
                b.contentType(MediaType.APPLICATION_JSON).content(body);
            }
            return mvc.perform(b);
        }
    }
}
