package es.docklite.docklitebackend.auth.controller;


import es.docklite.docklitebackend.auth.dto.AuthResponse;
import es.docklite.docklitebackend.auth.dto.LoginRequest;
import es.docklite.docklitebackend.auth.dto.RegisterRequest;
import es.docklite.docklitebackend.auth.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController{
    private final AuthService authService;


    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public AuthResponse register(@Valid @RequestBody RegisterRequest req){
        return authService.register(req);
    }

    @PostMapping("/login")
    public AuthResponse login(@Valid @RequestBody LoginRequest req) {
        return authService.login(req);
    }
}
