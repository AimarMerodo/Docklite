package es.docklite.docklitebackend.auth.controller;


import es.docklite.docklitebackend.auth.dto.AuthResponse;
import es.docklite.docklitebackend.auth.dto.LoginRequest;
import es.docklite.docklitebackend.auth.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/login")
    public AuthResponse login(@Valid @RequestBody LoginRequest req) {
        return authService.login(req);
    }
}
