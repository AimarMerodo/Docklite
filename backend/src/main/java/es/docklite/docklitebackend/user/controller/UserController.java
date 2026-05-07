package es.docklite.docklitebackend.user.controller;

import es.docklite.docklitebackend.common.dto.PageResponse;
import es.docklite.docklitebackend.user.dto.UpdateProfileRequest;
import es.docklite.docklitebackend.user.dto.UserDto;
import es.docklite.docklitebackend.user.entity.User;
import es.docklite.docklitebackend.user.repository.UserRepository;
import es.docklite.docklitebackend.user.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;


@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserController {
    private final UserRepository userRepository;
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
    @PreAuthorize("hasRole('ADMIN')")
    public PageResponse<UserDto> list(@RequestParam(defaultValue = "0") int page,
                                      @RequestParam(defaultValue = "20") int size) {
        return PageResponse.from(
                userRepository.findAll(PageRequest.of(page, size, Sort.by("id").descending()))
                        .map(UserDto::from)
        );
    }

}
