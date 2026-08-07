package es.docklite.docklitebackend.user.controller;

import es.docklite.docklitebackend.common.dto.PageResponse;
import es.docklite.docklitebackend.user.dto.UpdateProfileRequest;
import es.docklite.docklitebackend.user.dto.UserDto;
import es.docklite.docklitebackend.user.entity.User;
import es.docklite.docklitebackend.user.service.UserService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;


@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
@Validated
public class UserController {
    private final UserService userService;


    @GetMapping("/me")
    public UserDto me(Authentication auth){
        User user = (User) auth.getPrincipal();
        return UserDto.from(user);
    }

    @PutMapping("/me")
    public UserDto updateMe(Authentication auth, @Valid @RequestBody UpdateProfileRequest req) {
        User user = (User) auth.getPrincipal();
        return UserDto.from(userService.updatePassword(user, req));
    }

    @GetMapping
    // DEMO gets the admin read-only view of this screen.
    @PreAuthorize("hasAnyRole('ADMIN','DEMO')")
    public PageResponse<UserDto> list(
            @RequestParam(defaultValue = "0") @Min(value = 0, message = "page must be >= 0") int page,
            @RequestParam(defaultValue = "20") @Min(value = 1, message = "size must be >= 1") @Max(value = 100, message = "size must be <= 100") int size) {
        return PageResponse.from(
                userService.list(PageRequest.of(page, size, Sort.by("id").descending()))
        );
    }

}
