package es.docklite.docklitebackend.auth.controller;

import es.docklite.docklitebackend.auth.dto.AcceptInvitationRequest;
import es.docklite.docklitebackend.auth.dto.AuthResponse;
import es.docklite.docklitebackend.auth.dto.InvitationStatusDto;
import es.docklite.docklitebackend.auth.service.InvitationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/invitations")
@RequiredArgsConstructor
public class InvitationController {

    private final InvitationService invitationService;

    @GetMapping("/{token}")
    public InvitationStatusDto validate(@PathVariable String token) {
        return invitationService.validate(token);
    }

    @PostMapping("/{token}/accept")
    @ResponseStatus(HttpStatus.CREATED)
    public AuthResponse accept(@PathVariable String token,
                               @Valid @RequestBody AcceptInvitationRequest req) {
        return invitationService.accept(token, req);
    }
}
