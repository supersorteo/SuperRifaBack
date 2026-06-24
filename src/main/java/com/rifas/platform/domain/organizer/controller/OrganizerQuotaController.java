package com.rifas.platform.domain.organizer.controller;

import com.rifas.platform.domain.organizer.entity.OrganizerProfile;
import com.rifas.platform.domain.organizer.repository.OrganizerProfileRepository;
import com.rifas.platform.domain.vip.dto.OrganizerQuotaSummaryDto;
import com.rifas.platform.domain.vip.service.OrganizerQuotaService;
import com.rifas.platform.shared.exception.ResourceNotFoundException;
import com.rifas.platform.shared.security.UserDetailsImpl;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/organizer")
@RequiredArgsConstructor
public class OrganizerQuotaController {

    private final OrganizerQuotaService quotaService;
    private final OrganizerProfileRepository profileRepository;

    @GetMapping("/subscription-status")
    public ResponseEntity<OrganizerQuotaSummaryDto> subscriptionStatus() {
        UUID userId = ((UserDetailsImpl) SecurityContextHolder
                .getContext().getAuthentication().getPrincipal()).getId();
        OrganizerProfile profile = profileRepository.findByUserId(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Perfil no encontrado"));
        return ResponseEntity.ok(quotaService.getSummary(profile));
    }
}
