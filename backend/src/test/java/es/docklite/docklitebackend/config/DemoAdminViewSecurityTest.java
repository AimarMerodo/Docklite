package es.docklite.docklitebackend.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import es.docklite.docklitebackend.auth.controller.AdminInvitationController;
import es.docklite.docklitebackend.auth.dto.InvitationDto;
import es.docklite.docklitebackend.auth.jwt.JwtAuthFilter;
import es.docklite.docklitebackend.auth.jwt.JwtProvider;
import es.docklite.docklitebackend.auth.service.InvitationService;
import es.docklite.docklitebackend.common.exception.JsonAccessDeniedHandler;
import es.docklite.docklitebackend.common.exception.JsonAuthenticationEntryPoint;
import es.docklite.docklitebackend.user.controller.UserController;
import es.docklite.docklitebackend.user.entity.Role;
import es.docklite.docklitebackend.user.entity.User;
import es.docklite.docklitebackend.user.repository.UserRepository;
import es.docklite.docklitebackend.user.service.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.time.LocalDateTime;
import java.util.List;

import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Demo admin view: DEMO can read the admin screens (users, invitations) but
 * invitation tokens are masked and mutations stay forbidden.
 */
@WebMvcTest({UserController.class, AdminInvitationController.class})
@Import({SecurityConfig.class, JwtAuthFilter.class, JsonAuthenticationEntryPoint.class, JsonAccessDeniedHandler.class})
class DemoAdminViewSecurityTest {

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
    private UserService userService;
    @MockitoBean
    private InvitationService invitationService;
    @MockitoBean
    private JwtProvider jwtProvider;
    @MockitoBean
    private UserRepository userRepository;

    private static RequestPostProcessor authenticatedAs(Role role) {
        User user = User.builder()
                .username(role.name().toLowerCase())
                .email(role.name().toLowerCase() + "@docklite.local")
                .role(role)
                .build();
        var authorities = List.of(new SimpleGrantedAuthority("ROLE_" + role.name()));
        return authentication(new UsernamePasswordAuthenticationToken(user, null, authorities));
    }

    private static Page<InvitationDto> oneInvitation() {
        return new PageImpl<>(List.of(new InvitationDto(
                1L, "secret-token", "https://demo/invite/secret-token",
                1, 1, LocalDateTime.now().plusDays(7),
                false, false, false, true, 1L, LocalDateTime.now()
        )));
    }

    @Test
    void demoCanListUsers() throws Exception {
        when(userService.list(any())).thenReturn(Page.empty());

        mvc.perform(get("/api/v1/users").with(authenticatedAs(Role.DEMO)))
                .andExpect(status().isOk());
    }

    @Test
    void regularUserStillCannotListUsers() throws Exception {
        mvc.perform(get("/api/v1/users").with(authenticatedAs(Role.USER)))
                .andExpect(status().isForbidden());
    }

    @Test
    void demoSeesInvitationsWithMaskedTokens() throws Exception {
        when(invitationService.listAll(any())).thenReturn(oneInvitation());

        mvc.perform(get("/api/v1/admin/invitations").with(authenticatedAs(Role.DEMO)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].token").value(nullValue()))
                .andExpect(jsonPath("$.content[0].url").value(nullValue()))
                .andExpect(jsonPath("$.content[0].active").value(true));
    }

    @Test
    void adminStillSeesInvitationTokens() throws Exception {
        when(invitationService.listAll(any())).thenReturn(oneInvitation());

        mvc.perform(get("/api/v1/admin/invitations").with(authenticatedAs(Role.ADMIN)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].token").value("secret-token"));
    }

    @Test
    void demoCannotCreateInvitations() throws Exception {
        mvc.perform(post("/api/v1/admin/invitations").with(authenticatedAs(Role.DEMO))
                        .contentType("application/json").content("{}"))
                .andExpect(status().isForbidden());
    }
}
