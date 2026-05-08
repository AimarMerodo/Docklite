package es.docklite.docklitebackend.docker.controller;

import es.docklite.docklitebackend.common.dto.PageResponse;
import es.docklite.docklitebackend.docker.dto.ImageDto;
import es.docklite.docklitebackend.docker.dto.PullImageRequest;
import es.docklite.docklitebackend.docker.dto.RemoteImageInfoDto;
import es.docklite.docklitebackend.docker.dto.SearchResultDto;
import es.docklite.docklitebackend.docker.service.ImageService;
import es.docklite.docklitebackend.user.entity.User;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/images")
@RequiredArgsConstructor
@Validated
public class ImageController {

    private final ImageService imageService;

    @GetMapping
    public PageResponse<ImageDto> list(Authentication auth,
                                       @RequestParam(defaultValue = "0") @Min(value = 0, message = "page must be >= 0") int page,
                                       @RequestParam(defaultValue = "20") @Min(value = 1, message = "size must be >= 1") @Max(value = 100, message = "size must be <= 100") int size) {
        return PageResponse.of(
                imageService.list((User) auth.getPrincipal()),
                page,
                size
        );
    }

    @PostMapping("/pull")
    @ResponseStatus(HttpStatus.CREATED)
    public ImageDto pull(Authentication auth, @Valid @RequestBody PullImageRequest req) {
        return imageService.pull(req, (User) auth.getPrincipal());
    }

    @GetMapping("/{id}")
    public ImageDto inspect(Authentication auth, @PathVariable String id) {
        return imageService.inspect(id, (User) auth.getPrincipal());
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

    @GetMapping("/inspect-remote")
    public RemoteImageInfoDto inspectRemote(@RequestParam String ref) {
        return imageService.inspectRemote(ref);
    }
}
