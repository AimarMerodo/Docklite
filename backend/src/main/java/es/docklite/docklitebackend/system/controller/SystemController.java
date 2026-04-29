package es.docklite.docklitebackend.system.controller;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.model.Info;
import com.github.dockerjava.api.model.Version;
import es.docklite.docklitebackend.docker.dto.ContainerDto;
import es.docklite.docklitebackend.docker.service.ContainerService;
import es.docklite.docklitebackend.docker.service.ImageService;
import es.docklite.docklitebackend.docker.service.NetworkService;
import es.docklite.docklitebackend.docker.service.VolumeService;
import es.docklite.docklitebackend.system.dto.DashboardSummaryDto;
import es.docklite.docklitebackend.user.entity.User;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/system")
@RequiredArgsConstructor
public class SystemController {

    private final DockerClient dockerClient;
    private final ContainerService containerService;
    private final ImageService imageService;
    private final NetworkService networkService;
    private final VolumeService volumeService;

    @GetMapping("/info")
    public Info info() {
        return dockerClient.infoCmd().exec();
    }

    @GetMapping("/version")
    public Version version() {
        return dockerClient.versionCmd().exec();
    }

    @GetMapping("/dashboard")
    public DashboardSummaryDto dashboard(Authentication auth) {
        User user = (User) auth.getPrincipal();

        List<ContainerDto> containers = containerService.list(user, true);
        long running = containers.stream().filter(c -> "running".equals(c.state())).count();

        return new DashboardSummaryDto(
                containers.size(),
                running,
                containers.size() - running,
                imageService.list(user).size(),
                networkService.list(user).size(),
                volumeService.list(user).size()
        );
    }
}
