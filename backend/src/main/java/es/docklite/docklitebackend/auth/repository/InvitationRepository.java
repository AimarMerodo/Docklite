package es.docklite.docklitebackend.auth.repository;

import es.docklite.docklitebackend.auth.entity.Invitation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface InvitationRepository extends JpaRepository<Invitation, Long> {

    Optional<Invitation> findByToken(String token);
}
