package ru.netmon.testsupport;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;

import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import ru.netmon.contract.Role;

/**
 * Запросы к API от имени роли (architecture.md §12.2): пользователь и csrf() на каждом запросе.
 * Тип principal меняет T1.05 вместе с этим классом.
 */
public class Api {

    private final MockMvc mvc;

    public Api(MockMvc mvc) {
        this.mvc = mvc;
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
                    .with(user("test-" + role.name().toLowerCase()).roles(role.name()))
                    .with(csrf());
            if (body != null) {
                b.contentType(MediaType.APPLICATION_JSON).content(body);
            }
            return mvc.perform(b);
        }
    }
}
