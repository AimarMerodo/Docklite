package es.docklite.docklitebackend.docker.controller;

import es.docklite.docklitebackend.common.dto.PageResponse;
import es.docklite.docklitebackend.docker.dto.CreateNetworkRequest;
import es.docklite.docklitebackend.docker.dto.NetworkDto;
import es.docklite.docklitebackend.docker.service.NetworkService;
import es.docklite.docklitebackend.user.entity.User;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/networks")
@RequiredArgsConstructor
@Validated
public class NetworkController {

    private final NetworkService networkService;

    @GetMapping
    public PageResponse<NetworkDto> list(Authentication auth,
                                         @RequestParam(defaultValue = "0") @Min(value = 0, message = "page must be >= 0") int page,
                                         @RequestParam(defaultValue = "20") @Min(value = 1, message = "size must be >= 1") @Max(value = 100, message = "size must be <= 100") int size) {
        return PageResponse.of(
                networkService.list((User) auth.getPrincipal()),
                page,
                size
        );
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
