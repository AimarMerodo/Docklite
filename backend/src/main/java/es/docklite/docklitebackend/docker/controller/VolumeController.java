package es.docklite.docklitebackend.docker.controller;

import es.docklite.docklitebackend.common.dto.PageResponse;
import es.docklite.docklitebackend.docker.dto.CreateVolumeRequest;
import es.docklite.docklitebackend.docker.dto.VolumeDto;
import es.docklite.docklitebackend.docker.service.VolumeService;
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
@RequestMapping("/api/v1/volumes")
@RequiredArgsConstructor
@Validated
public class VolumeController {

    private final VolumeService volumeService;

    @GetMapping
    public PageResponse<VolumeDto> list(Authentication auth,
                                        @RequestParam(defaultValue = "0") @Min(value = 0, message = "page must be >= 0") int page,
                                        @RequestParam(defaultValue = "20") @Min(value = 1, message = "size must be >= 1") @Max(value = 100, message = "size must be <= 100") int size) {
        return PageResponse.of(
                volumeService.list((User) auth.getPrincipal()),
                page,
                size
        );
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public VolumeDto create(Authentication auth, @Valid @RequestBody CreateVolumeRequest req) {
        return volumeService.create(req, (User) auth.getPrincipal());
    }

    @GetMapping("/{name}")
    public VolumeDto inspect(Authentication auth, @PathVariable String name) {
        return volumeService.inspect(name, (User) auth.getPrincipal());
    }

    @DeleteMapping("/{name}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void remove(Authentication auth, @PathVariable String name) {
        volumeService.remove(name, (User) auth.getPrincipal());
    }
}
