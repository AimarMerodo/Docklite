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
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@RestController
@RequestMapping("/api/v1/system")
@RequiredArgsConstructor
public class SystemController {

    private final DockerClient dockerClient;
    private final ContainerService containerService;
    private final ImageService imageService;
    private final NetworkService networkService;
    private final VolumeService volumeService;

    // Virtual threads: the summary fan-out is blocking IO, and the default
    // ForkJoinPool sizes itself to the host's core count, which is exactly
    // what we don't want to depend on.
    private final ExecutorService summaryExecutor = Executors.newVirtualThreadPerTaskExecutor();

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

        // The four listings are independent daemon round-trips. On a busy
        // host each takes hundreds of ms, so running them sequentially made
        // this endpoint cost their SUM (~1.2s on the demo VPS); concurrent
        // and using the count-only service paths, it costs roughly the
        // slowest single daemon call.
        CompletableFuture<List<ContainerDto>> containersFuture =
                CompletableFuture.supplyAsync(() -> containerService.list(user, true), summaryExecutor);
        CompletableFuture<Long> imageCount =
                CompletableFuture.supplyAsync(() -> imageService.countVisible(user), summaryExecutor);
        CompletableFuture<Long> networkCount =
                CompletableFuture.supplyAsync(() -> networkService.countVisible(user), summaryExecutor);
        CompletableFuture<Long> volumeCount =
                CompletableFuture.supplyAsync(() -> volumeService.countVisible(user), summaryExecutor);

        try {
            List<ContainerDto> containers = containersFuture.join();
            long running = containers.stream().filter(c -> "running".equals(c.state())).count();

            return new DashboardSummaryDto(
                    containers.size(),
                    running,
                    containers.size() - running,
                    imageCount.join(),
                    networkCount.join(),
                    volumeCount.join()
            );
        } catch (CompletionException e) {
            // join() wraps failures; rethrow the original exception so the
            // global handler keeps mapping Docker errors as before.
            if (e.getCause() instanceof RuntimeException cause) {
                throw cause;
            }
            throw e;
        }
    }
}
