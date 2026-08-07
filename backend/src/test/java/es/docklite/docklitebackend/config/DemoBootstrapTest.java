package es.docklite.docklitebackend.config;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.ListContainersCmd;
import es.docklite.docklitebackend.docker.service.ContainerService;
import es.docklite.docklitebackend.docker.service.ResourceOwnershipService;
import es.docklite.docklitebackend.user.entity.Role;
import es.docklite.docklitebackend.user.entity.User;
import es.docklite.docklitebackend.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DemoBootstrapTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private org.springframework.security.crypto.password.PasswordEncoder passwordEncoder;
    @Mock
    private ContainerService containerService;
    @Mock
    private ResourceOwnershipService ownershipService;
    @Mock
    private DockerClient dockerClient;

    @InjectMocks
    private DemoBootstrap bootstrap;

    private static final String DEMO_EMAIL = "demo@docklite.local";

    @BeforeEach
    void configureProperties() {
        ReflectionTestUtils.setField(bootstrap, "demoUsername", "demo");
        ReflectionTestUtils.setField(bootstrap, "demoEmail", DEMO_EMAIL);
        ReflectionTestUtils.setField(bootstrap, "demoPassword", "");
    }

    private void stubEmptyDockerContainerList() {
        ListContainersCmd cmd = mock(ListContainersCmd.class);
        when(dockerClient.listContainersCmd()).thenReturn(cmd);
        when(cmd.withShowAll(true)).thenReturn(cmd);
        when(cmd.withNameFilter(anyList())).thenReturn(cmd);
        when(cmd.exec()).thenReturn(List.of());
    }

    @Test
    void seedDoesNotRecreateExistingDemoUser() {
        User existing = User.builder().username("demo").email(DEMO_EMAIL).role(Role.DEMO).build();
        when(userRepository.findByEmail(DEMO_EMAIL)).thenReturn(Optional.of(existing));
        stubEmptyDockerContainerList();

        bootstrap.seed();

        verify(userRepository, never()).save(any());
    }

    @Test
    void seedCreatesDemoUserWithDemoRole() {
        when(userRepository.findByEmail(DEMO_EMAIL)).thenReturn(Optional.empty());
        when(passwordEncoder.encode(any())).thenReturn("hash");
        when(userRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        stubEmptyDockerContainerList();

        bootstrap.seed();

        ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(saved.capture());
        assertThat(saved.getValue().getRole()).isEqualTo(Role.DEMO);
        assertThat(saved.getValue().getEmail()).isEqualTo(DEMO_EMAIL);
        assertThat(saved.getValue().getUsername()).isEqualTo("demo");
    }

    @Test
    void seedSwallowsDockerFailuresSoStartupIsNotAborted() {
        User existing = User.builder().username("demo").email(DEMO_EMAIL).role(Role.DEMO).build();
        when(userRepository.findByEmail(DEMO_EMAIL)).thenReturn(Optional.of(existing));
        when(dockerClient.listContainersCmd()).thenThrow(new RuntimeException("Docker daemon not reachable"));

        assertDoesNotThrow(() -> bootstrap.seed());
        verify(containerService, never()).create(any(), any());
    }
}
