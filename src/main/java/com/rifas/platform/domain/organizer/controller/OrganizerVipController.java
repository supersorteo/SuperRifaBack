package com.rifas.platform.domain.organizer.controller;

import com.rifas.platform.domain.organizer.entity.OrganizerProfile;
import com.rifas.platform.domain.organizer.repository.OrganizerProfileRepository;
import com.rifas.platform.domain.vip.dto.*;
import com.rifas.platform.domain.vip.service.OrganizerQuotaService;
import com.rifas.platform.domain.vip.service.VipCodeService;
import com.rifas.platform.domain.vip.service.VipPackageService;
import com.rifas.platform.domain.vip.service.VipPurchaseService;
import com.rifas.platform.shared.exception.ResourceNotFoundException;
import com.rifas.platform.shared.security.UserDetailsImpl;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/organizer")
@RequiredArgsConstructor
public class OrganizerVipController {

    private final VipPackageService      packageService;
    private final VipCodeService         codeService;
    private final OrganizerQuotaService  quotaService;
    private final VipPurchaseService     purchaseService;
    private final OrganizerProfileRepository profileRepository;

    // ── Catálogo de paquetes ──────────────────────────────────────────────

    @GetMapping("/vip-packages")
    public ResponseEntity<List<VipPackageDto>> listPackages() {
        return ResponseEntity.ok(packageService.findAllActive());
    }

    // ── Canje de código manual ────────────────────────────────────────────

    @PostMapping("/vip-codes/redeem")
    public ResponseEntity<OrganizerQuotaSummaryDto> redeem(@Valid @RequestBody RedeemVipCodeRequest req) {
        OrganizerProfile organizer = currentOrganizer();
        codeService.redeem(req.code(), organizer);
        return ResponseEntity.ok(quotaService.getSummary(organizer));
    }

    // ── Compra VIP por Mercado Pago ───────────────────────────────────────

    @PostMapping("/vip-purchases/preference")
    public ResponseEntity<VipPreferenceResponse> createPreference(
            @Valid @RequestBody CreateVipPreferenceRequest req) {
        OrganizerProfile organizer = currentOrganizer();
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(purchaseService.createPreference(req, organizer));
    }

    @GetMapping("/vip-purchases/history")
    public ResponseEntity<List<VipPurchaseDto>> purchaseHistory() {
        UUID userId = currentUserId();
        OrganizerProfile organizer = profileRepository.findByUserId(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Perfil no encontrado"));
        return ResponseEntity.ok(purchaseService.getHistoryForOrganizer(organizer.getId()));
    }

    // ── Helper ────────────────────────────────────────────────────────────

    private OrganizerProfile currentOrganizer() {
        return profileRepository.findByUserId(currentUserId())
                .orElseThrow(() -> new ResourceNotFoundException("Perfil no encontrado"));
    }

    private UUID currentUserId() {
        return ((UserDetailsImpl) SecurityContextHolder
                .getContext().getAuthentication().getPrincipal()).getId();
    }
}
