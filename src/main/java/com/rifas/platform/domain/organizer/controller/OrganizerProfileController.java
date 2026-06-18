package com.rifas.platform.domain.organizer.controller;

import com.rifas.platform.domain.organizer.entity.OrganizerProfile;
import com.rifas.platform.domain.organizer.repository.OrganizerProfileRepository;
import com.rifas.platform.domain.raffle.service.ImageStorageService;
import com.rifas.platform.domain.user.repository.UserRepository;
import com.rifas.platform.shared.exception.BusinessException;
import com.rifas.platform.shared.exception.ResourceNotFoundException;
import com.rifas.platform.shared.security.UserDetailsImpl;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.UUID;

@RestController
@RequestMapping("/api/organizer/profile")
@RequiredArgsConstructor
public class OrganizerProfileController {

    private final OrganizerProfileRepository profileRepository;
    private final ImageStorageService imageStorageService;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public record OrganizerProfileDto(
            UUID id,
            String email,
            String fullName,
            String businessName,
            String phone,
            String bio,
            String avatarUrl,
            String instagramHandle,
            String whatsappNumber,
            LocalDateTime createdAt
    ) {}

    public record UpdateProfileRequest(
            @Size(max = 100) String businessName,
            @Size(max = 20)  String phone,
            @Size(max = 1000) String bio,
            @Size(max = 50)  String instagramHandle,
            @Size(max = 20)  String whatsappNumber
    ) {}

    public record ChangePasswordRequest(
            @NotBlank String currentPassword,
            @NotBlank @Size(min = 6) String newPassword
    ) {}

    @GetMapping
    public ResponseEntity<OrganizerProfileDto> get() {
        return ResponseEntity.ok(toDto(currentProfile()));
    }

    @PutMapping
    public ResponseEntity<OrganizerProfileDto> update(@Valid @RequestBody UpdateProfileRequest req) {
        OrganizerProfile profile = currentProfile();
        profile.setBusinessName(req.businessName());
        profile.setPhone(req.phone());
        profile.setBio(req.bio());
        profile.setInstagramHandle(req.instagramHandle());
        profile.setWhatsappNumber(req.whatsappNumber());
        profileRepository.save(profile);
        return ResponseEntity.ok(toDto(reloadProfile(profile.getUser().getId())));
    }

    @PatchMapping("/change-password")
    public ResponseEntity<Void> changePassword(@Valid @RequestBody ChangePasswordRequest req) {
        OrganizerProfile profile = currentProfile();
        var user = profile.getUser();
        if (!passwordEncoder.matches(req.currentPassword(), user.getPasswordHash())) {
            throw new BusinessException("La contrasena actual no es correcta");
        }
        user.setPasswordHash(passwordEncoder.encode(req.newPassword()));
        userRepository.save(user);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/avatar")
    public ResponseEntity<OrganizerProfileDto> uploadAvatar(@RequestParam("file") MultipartFile file) throws IOException {
        OrganizerProfile profile = currentProfile();
        String previousAvatarRef = profile.getAvatarPublicId();
        if ((previousAvatarRef == null || previousAvatarRef.isBlank())
                && profile.getAvatarUrl() != null
                && !profile.getAvatarUrl().isBlank()) {
            previousAvatarRef = profile.getAvatarUrl();
        }
        if (previousAvatarRef != null && !previousAvatarRef.isBlank()) {
            try { imageStorageService.delete(previousAvatarRef); } catch (Exception ignored) {}
        }
        ImageStorageService.UploadResult result = imageStorageService.uploadAvatar(file, profile.getId());
        profile.setAvatarPublicId(result.publicId());
        profile.setAvatarUrl(result.url());
        profileRepository.save(profile);
        return ResponseEntity.ok(toDto(reloadProfile(profile.getUser().getId())));
    }

    private OrganizerProfile currentProfile() {
        UserDetailsImpl ud = (UserDetailsImpl) SecurityContextHolder
                .getContext().getAuthentication().getPrincipal();
        return reloadProfile(ud.getId());
    }

    private OrganizerProfile reloadProfile(UUID userId) {
        return profileRepository.findByUserIdWithUser(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Perfil no encontrado"));
    }

    private OrganizerProfileDto toDto(OrganizerProfile p) {
        return new OrganizerProfileDto(
                p.getId(),
                p.getUser().getEmail(),
                p.getUser().getFullName(),
                p.getBusinessName(),
                p.getPhone(),
                p.getBio(),
                p.getAvatarUrl(),
                p.getInstagramHandle(),
                p.getWhatsappNumber(),
                p.getCreatedAt()
        );
    }
}
