package es.docklite.docklitebackend.docker.controller;

import es.docklite.docklitebackend.common.dto.PageResponse;
import es.docklite.docklitebackend.docker.dto.ContainerDetailDto;
import es.docklite.docklitebackend.docker.dto.ContainerDto;
import es.docklite.docklitebackend.docker.dto.ContainerStatsDto;
import es.docklite.docklitebackend.docker.dto.PortInUseDto;
import es.docklite.docklitebackend.docker.dto.CreateContainerRequest;
import es.docklite.docklitebackend.docker.dto.RenameContainerRequest;
import es.docklite.docklitebackend.docker.dto.UpdateCommandRequest;
import es.docklite.docklitebackend.docker.dto.UpdateEnvRequest;
import es.docklite.docklitebackend.docker.dto.UpdateHealthcheckRequest;
import es.docklite.docklitebackend.docker.dto.UpdateMountsRequest;
import es.docklite.docklitebackend.docker.dto.UpdatePortsRequest;
import es.docklite.docklitebackend.docker.dto.UpdateResourcesRequest;
import es.docklite.docklitebackend.docker.service.ContainerService;
import es.docklite.docklitebackend.user.entity.User;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/containers")
@RequiredArgsConstructor
@Validated
public class ContainerController {

    private final ContainerService containerService;

    @GetMapping
    public PageResponse<ContainerDto> list(Authentication auth,
                                           @RequestParam(defaultValue = "true") boolean all,
                                           @RequestParam(defaultValue = "0") @Min(value = 0, message = "page must be >= 0") int page,
                                           @RequestParam(defaultValue = "20") @Min(value = 1, message = "size must be >= 1") @Max(value = 100, message = "size must be <= 100") int size) {
        return PageResponse.of(
                containerService.list((User) auth.getPrincipal(), all),
                page,
                size
        );
    }

    @GetMapping("/ports-in-use")
    public List<PortInUseDto> portsInUse(Authentication auth) {
        return containerService.portsInUse((User) auth.getPrincipal());
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ContainerDetailDto create(Authentication auth,
                                     @Valid @RequestBody CreateContainerRequest req) {
        return containerService.create(req, (User) auth.getPrincipal());
    }

    @GetMapping("/{id}")
    public ContainerDetailDto inspect(Authentication auth, @PathVariable String id) {
        return containerService.inspect(id, (User) auth.getPrincipal());
    }

    @PostMapping("/{id}/start")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void start(Authentication auth, @PathVariable String id) {
        containerService.start(id, (User) auth.getPrincipal());
    }

    @PostMapping("/{id}/stop")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void stop(Authentication auth, @PathVariable String id) {
        containerService.stop(id, (User) auth.getPrincipal());
    }

    @PostMapping("/{id}/restart")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void restart(Authentication auth, @PathVariable String id) {
        containerService.restart(id, (User) auth.getPrincipal());
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void remove(Authentication auth, @PathVariable String id) {
        containerService.remove(id, (User) auth.getPrincipal());
    }

    @GetMapping("/{id}/logs")
    public String logs(Authentication auth, @PathVariable String id,
                       @RequestParam(defaultValue = "100") int tail) {
        return containerService.logs(id, (User) auth.getPrincipal(), tail);
    }

    @GetMapping("/{id}/stats")
    public ContainerStatsDto stats(Authentication auth, @PathVariable String id) {
        return containerService.stats(id, (User) auth.getPrincipal());
    }

    @PutMapping("/{id}/mounts")
    public ContainerDetailDto updateMounts(Authentication auth,
                                           @PathVariable String id,
                                           @Valid @RequestBody UpdateMountsRequest req) {
        return containerService.updateMounts(id, req, (User) auth.getPrincipal());
    }

    @PutMapping("/{id}/ports")
    public ContainerDetailDto updatePorts(Authentication auth,
                                          @PathVariable String id,
                                          @Valid @RequestBody UpdatePortsRequest req) {
        return containerService.updatePorts(id, req, (User) auth.getPrincipal());
    }

    @PutMapping("/{id}/env")
    public ContainerDetailDto updateEnv(Authentication auth,
                                        @PathVariable String id,
                                        @Valid @RequestBody UpdateEnvRequest req) {
        return containerService.updateEnv(id, req, (User) auth.getPrincipal());
    }

    @PutMapping("/{id}/command")
    public ContainerDetailDto updateCommand(Authentication auth,
                                            @PathVariable String id,
                                            @Valid @RequestBody UpdateCommandRequest req) {
        return containerService.updateCommand(id, req, (User) auth.getPrincipal());
    }

    @PutMapping("/{id}/healthcheck")
    public ContainerDetailDto updateHealthcheck(Authentication auth,
                                                @PathVariable String id,
                                                @Valid @RequestBody UpdateHealthcheckRequest req) {
        return containerService.updateHealthcheck(id, req, (User) auth.getPrincipal());
    }

    @PatchMapping("/{id}/resources")
    public ContainerDetailDto updateResources(Authentication auth,
                                              @PathVariable String id,
                                              @Valid @RequestBody UpdateResourcesRequest req) {
        return containerService.updateResources(id, req, (User) auth.getPrincipal());
    }

    @PostMapping("/{id}/rename")
    public ContainerDetailDto rename(Authentication auth,
                                     @PathVariable String id,
                                     @Valid @RequestBody RenameContainerRequest req) {
        return containerService.rename(id, req, (User) auth.getPrincipal());
    }
}
