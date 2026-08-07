package es.docklite.docklitebackend.auth;

import es.docklite.docklitebackend.auth.controller.AuthController;
import es.docklite.docklitebackend.auth.jwt.JwtAuthFilter;
import es.docklite.docklitebackend.auth.jwt.JwtProvider;
import es.docklite.docklitebackend.auth.service.AuthService;
import es.docklite.docklitebackend.common.exception.JsonAccessDeniedHandler;
import es.docklite.docklitebackend.common.exception.JsonAuthenticationEntryPoint;
import es.docklite.docklitebackend.config.SecurityConfig;
import es.docklite.docklitebackend.user.repository.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AuthController.class)
@Import({SecurityConfig.class, JwtAuthFilter.class, JsonAuthenticationEntryPoint.class, JsonAccessDeniedHandler.class})
class AuthDemoEndpointTest {

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
    private AuthService authService;
    @MockitoBean
    private JwtProvider jwtProvider;
    @MockitoBean
    private UserRepository userRepository;

    @Test
    void demoStatusReflectsEnabledFlag() throws Exception {
        when(authService.isDemoEnabled()).thenReturn(true);

        mvc.perform(get("/api/v1/auth/demo"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(true));
    }

    @Test
    void demoStatusReflectsDisabledFlag() throws Exception {
        when(authService.isDemoEnabled()).thenReturn(false);

        mvc.perform(get("/api/v1/auth/demo"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(false));
    }

    @Test
    void demoLoginReturns404WhenDemoModeIsOff() throws Exception {
        when(authService.demoLogin()).thenThrow(new EntityNotFoundException("Demo mode is not enabled"));

        mvc.perform(post("/api/v1/auth/demo"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Demo mode is not enabled"));
    }
}
