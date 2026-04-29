package es.docklite.docklitebackend.docker.controller;

import es.docklite.docklitebackend.docker.dto.CreateNetworkRequest;
import es.docklite.docklitebackend.docker.dto.NetworkDto;
import es.docklite.docklitebackend.docker.service.NetworkService;
import es.docklite.docklitebackend.user.entity.User;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/networks")
@RequiredArgsConstructor
public class NetworkController {

    private final NetworkService networkService;

    @GetMapping
    public List<NetworkDto> list(Authentication auth) {
        return networkService.list((User) auth.getPrincipal());
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public NetworkDto create(Authentication auth, @Valid @RequestBody CreateNetworkRequest req) {
        return networkService.create(req, (User) auth.getPrincipal());
    }

    @GetMapping("/{id}")
    public NetworkDto inspect(Authentication auth, @PathVariable String id) {
        return networkService.inspect(id, (User) auth.getPrincipal());
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void remove(Authentication auth, @PathVariable String id) {
        networkService.remove(id, (User) auth.getPrincipal());
    }

    @PostMapping("/{id}/connect/{containerId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void connect(Authentication auth,
                        @PathVariable String id,
                        @PathVariable String containerId) {
        networkService.connectContainer(id, containerId, (User) auth.getPrincipal());
    }

    @PostMapping("/{id}/disconnect/{containerId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void disconnect(Authentication auth,
                           @PathVariable String id,
                           @PathVariable String containerId) {
        networkService.disconnectContainer(id, containerId, (User) auth.getPrincipal());
    }
}
