package es.docklite.docklitebackend.auth.controller;

import es.docklite.docklitebackend.auth.dto.CreateInvitationRequest;
import es.docklite.docklitebackend.auth.dto.InvitationDto;
import es.docklite.docklitebackend.auth.service.InvitationService;
import es.docklite.docklitebackend.common.dto.PageResponse;
import es.docklite.docklitebackend.user.entity.User;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/admin/invitations")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class AdminInvitationController {

    private final InvitationService invitationService;

    @GetMapping
    public PageResponse<InvitationDto> list(@RequestParam(defaultValue = "0") int page,
                                            @RequestParam(defaultValue = "20") int size) {
        return PageResponse.from(
                invitationService.listAll(PageRequest.of(page, size, Sort.by("id").descending()))
        );
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public InvitationDto create(Authentication auth,
                                @Valid @RequestBody CreateInvitationRequest req) {
        return invitationService.create(req, (User) auth.getPrincipal());
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void cancel(@PathVariable Long id) {
        invitationService.cancel(id);
    }
}
