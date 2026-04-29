package es.docklite.docklitebackend.docker.controller;

import es.docklite.docklitebackend.docker.dto.CreateVolumeRequest;
import es.docklite.docklitebackend.docker.dto.VolumeDto;
import es.docklite.docklitebackend.docker.service.VolumeService;
import es.docklite.docklitebackend.user.entity.User;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/volumes")
@RequiredArgsConstructor
public class VolumeController {

    private final VolumeService volumeService;

    @GetMapping
    public List<VolumeDto> list(Authentication auth) {
        return volumeService.list((User) auth.getPrincipal());
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
