package es.docklite.docklitebackend.user.controller;

import es.docklite.docklitebackend.user.dto.UserDto;
import es.docklite.docklitebackend.user.entity.User;
import es.docklite.docklitebackend.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;


@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserController {
    private final UserRepository userRepository;


    @GetMapping("/me")
    public UserDto me(Authentication auth){
        User user = (User) auth.getPrincipal();
        return UserDto.from(user);
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public List<UserDto> list(){
        return userRepository.findAll().stream()
                .map(UserDto::from)
                .toList();
    }

}
