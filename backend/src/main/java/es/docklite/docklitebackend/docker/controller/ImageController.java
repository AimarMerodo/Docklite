package es.docklite.docklitebackend.docker.controller;

import es.docklite.docklitebackend.docker.dto.ImageDto;
import es.docklite.docklitebackend.docker.dto.PullImageRequest;
import es.docklite.docklitebackend.docker.dto.SearchResultDto;
import es.docklite.docklitebackend.docker.service.ImageService;
import es.docklite.docklitebackend.user.entity.User;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/images")
@RequiredArgsConstructor
public class ImageController {

    private final ImageService imageService;

    @GetMapping
    public List<ImageDto> list(Authentication auth) {
        return imageService.list((User) auth.getPrincipal());
    }

    @PostMapping("/pull")
    @ResponseStatus(HttpStatus.CREATED)
    public ImageDto pull(Authentication auth, @Valid @RequestBody PullImageRequest req) {
        return imageService.pull(req, (User) auth.getPrincipal());
    }

    @GetMapping("/{id}")
    public ImageDto inspect(@PathVariable String id) {
        return imageService.inspect(id);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void remove(Authentication auth, @PathVariable String id) {
        imageService.remove(id, (User) auth.getPrincipal());
    }

    @GetMapping("/search")
    public List<SearchResultDto> search(@RequestParam String q) {
        return imageService.search(q);
    }
}
