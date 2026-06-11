package com.rifas.platform.domain.admin.controller;

import com.rifas.platform.domain.admin.dto.AdminOrganizerDetailDto;
import com.rifas.platform.domain.admin.dto.AdminOrganizerDto;
import com.rifas.platform.domain.admin.service.AdminOrganizerService;
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
