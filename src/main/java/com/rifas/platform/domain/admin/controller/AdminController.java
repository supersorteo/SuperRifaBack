package com.rifas.platform.domain.admin.controller;

import com.rifas.platform.domain.admin.dto.AdminOrganizerDetailDto;
import com.rifas.platform.domain.admin.dto.AdminOrganizerDto;
import com.rifas.platform.domain.admin.service.AdminOrganizerService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminController {

    private final AdminOrganizerService adminOrganizerService;

    public record ResetPasswordRequest(
            @NotBlank @Size(min = 6, message = "La contraseña debe tener al menos 6 caracteres") String newPassword
    ) {}

    public record ResetPasswordByEmailRequest(
            @NotBlank @Email String email,
            @NotBlank @Size(min = 6, message = "La contraseña debe tener al menos 6 caracteres") String newPassword
    ) {}

    @PatchMapping("/organizers/{id}/reset-password")
    public ResponseEntity<Void> resetPasswordById(@PathVariable UUID id,
                                                   @Valid @RequestBody ResetPasswordRequest req) {
        adminOrganizerService.resetPasswordById(id, req.newPassword());
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/users/reset-password")
    public ResponseEntity<Void> resetPasswordByEmail(@Valid @RequestBody ResetPasswordByEmailRequest req) {
        adminOrganizerService.resetPasswordByEmail(req.email(), req.newPassword());
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/organizers")
    public ResponseEntity<List<AdminOrganizerDto>> getOrganizers() {
        return ResponseEntity.ok(adminOrganizerService.getAllOrganizers());
    }

    @GetMapping("/organizers/{id}")
    public ResponseEntity<AdminOrganizerDetailDto> getOrganizerDetail(@PathVariable UUID id) {
        return ResponseEntity.ok(adminOrganizerService.getDetail(id));
    }

    @DeleteMapping("/organizers/{id}")
    public ResponseEntity<Void> deleteOrganizer(@PathVariable UUID id) {
        adminOrganizerService.deleteOrganizer(id);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/raffles/{id}")
    public ResponseEntity<Void> deleteRaffle(@PathVariable UUID id) {
        adminOrganizerService.deleteRaffle(id);
        return ResponseEntity.noContent().build();
    }
}
