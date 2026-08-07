package es.docklite.docklitebackend.config;

import es.docklite.docklitebackend.auth.jwt.JwtAuthFilter;
import es.docklite.docklitebackend.auth.jwt.JwtProvider;
import es.docklite.docklitebackend.common.exception.JsonAccessDeniedHandler;
import es.docklite.docklitebackend.common.exception.JsonAuthenticationEntryPoint;
import es.docklite.docklitebackend.common.exception.SecurityMessages;
import es.docklite.docklitebackend.docker.controller.ContainerController;
import es.docklite.docklitebackend.docker.service.ContainerService;
import es.docklite.docklitebackend.user.entity.Role;
import es.docklite.docklitebackend.user.entity.User;
import es.docklite.docklitebackend.user.repository.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Demo mode enforcement: ROLE_DEMO can read /api/v1/** but every mutating
 * method is rejected with 403 and the demo-specific message.
 */
@WebMvcTest(ContainerController.class)
@Import({SecurityConfig.class, JwtAuthFilter.class, JsonAuthenticationEntryPoint.class, JsonAccessDeniedHandler.class})
class DemoModeSecurityTest {

    /** The WebMvcTest slice doesn't provide the ObjectMapper bean that RateLimitFilter and JsonAccessDeniedHandler need. */
    @TestConfiguration
    static class SliceConfig {
        @Bean
        ObjectMapper objectMapper() {
            return new ObjectMapper();
        }
    }

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private ContainerService containerService;
    @MockitoBean
    private JwtProvider jwtProvider;
    @MockitoBean
    private UserRepository userRepository;

    /** Controllers cast the principal to the domain User, so build a real one. */
    private static RequestPostProcessor authenticatedAs(Role role) {
        User user = User.builder()
                .username(role.name().toLowerCase())
                .email(role.name().toLowerCase() + "@docklite.local")
                .role(role)
                .build();
        var authorities = List.of(new SimpleGrantedAuthority("ROLE_" + role.name()));
        return authentication(new UsernamePasswordAuthenticationToken(user, null, authorities));
    }

    @Test
    void demoUserCannotMutate() throws Exception {
        mvc.perform(post("/api/v1/containers/abc123/start").with(authenticatedAs(Role.DEMO)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value(SecurityMessages.DEMO_READ_ONLY));
    }

    @Test
    void demoUserCanRead() throws Exception {
        when(containerService.list(any(), anyBoolean())).thenReturn(List.of());

        mvc.perform(get("/api/v1/containers").with(authenticatedAs(Role.DEMO)))
                .andExpect(status().isOk());
    }

    @Test
    void regularUserCanStillMutate() throws Exception {
        mvc.perform(post("/api/v1/containers/abc123/start").with(authenticatedAs(Role.USER)))
                .andExpect(status().isNoContent());
    }
}
