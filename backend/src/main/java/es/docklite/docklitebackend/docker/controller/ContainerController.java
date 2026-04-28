package es.docklite.docklitebackend.docker.controller;

import es.docklite.docklitebackend.docker.dto.ContainerDto;
import es.docklite.docklitebackend.docker.dto.CreateContainerRequest;
import es.docklite.docklitebackend.docker.service.ContainerService;
import es.docklite.docklitebackend.user.entity.User;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/containers")
@RequiredArgsConstructor
public class ContainerController {

    private final ContainerService containerService;

    @GetMapping
    public List<ContainerDto> list(Authentication auth,
                                   @RequestParam(defaultValue = "true") boolean all) {
        return containerService.list((User) auth.getPrincipal(), all);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ContainerDto create(Authentication auth,
                               @Valid @RequestBody CreateContainerRequest req) {
        return containerService.create(req, (User) auth.getPrincipal());
    }

    @GetMapping("/{id}")
    public ContainerDto inspect(Authentication auth, @PathVariable String id) {
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
}
