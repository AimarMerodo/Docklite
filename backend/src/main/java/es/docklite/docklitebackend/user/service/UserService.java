package es.docklite.docklitebackend.user.service;

import es.docklite.docklitebackend.common.exception.InvalidCredentialsException;
import es.docklite.docklitebackend.user.dto.UpdateProfileRequest;
import es.docklite.docklitebackend.user.entity.User;
import es.docklite.docklitebackend.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public User updatePassword(User currentUser, UpdateProfileRequest req) {
        if (!passwordEncoder.matches(req.currentPassword(), currentUser.getPasswordHash())) {
            throw new InvalidCredentialsException("Current password is incorrect");
        }
        currentUser.setPasswordHash(passwordEncoder.encode(req.newPassword()));
        return userRepository.save(currentUser);
    }
}
