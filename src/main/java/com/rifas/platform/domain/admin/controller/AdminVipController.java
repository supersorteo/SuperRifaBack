package com.rifas.platform.domain.admin.controller;

import com.rifas.platform.domain.vip.dto.*;
import com.rifas.platform.domain.vip.service.VipCodeService;
import com.rifas.platform.domain.vip.service.VipPackageService;
import com.rifas.platform.domain.vip.service.VipPurchaseService;
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
@RequestMapping("/api/admin/vip")
@RequiredArgsConstructor
public class AdminVipController {

    private final VipPackageService  packageService;
    private final VipCodeService     codeService;
    private final VipPurchaseService purchaseService;

    // ── Packages ──────────────────────────────────────────────────────────

    @GetMapping("/packages")
    public ResponseEntity<List<VipPackageDto>> listPackages() {
        return ResponseEntity.ok(packageService.findAll());
    }

    @PostMapping("/packages")
    public ResponseEntity<VipPackageDto> createPackage(@Valid @RequestBody VipPackageRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(packageService.create(req));
    }

    @PutMapping("/packages/{id}")
    public ResponseEntity<VipPackageDto> updatePackage(@PathVariable UUID id,
                                                       @Valid @RequestBody VipPackageRequest req) {
        return ResponseEntity.ok(packageService.update(id, req));
    }

    @PatchMapping("/packages/{id}/toggle")
    public ResponseEntity<VipPackageDto> togglePackage(@PathVariable UUID id) {
        return ResponseEntity.ok(packageService.toggleActive(id));
    }

    // ── Codes ─────────────────────────────────────────────────────────────

    @GetMapping("/codes")
    public ResponseEntity<List<VipCodeResponse>> listCodes() {
        return ResponseEntity.ok(codeService.findAll());
    }

    @PostMapping("/codes")
    public ResponseEntity<VipCodeResponse> createCode(@Valid @RequestBody CreateVipCodeRequest req) {
        UUID adminId = ((UserDetailsImpl) SecurityContextHolder
                .getContext().getAuthentication().getPrincipal()).getId();
        return ResponseEntity.status(HttpStatus.CREATED).body(codeService.createManual(req, adminId));
    }

    @PostMapping("/codes/{id}/assign")
    public ResponseEntity<VipCodeResponse> assignCode(@PathVariable UUID id,
                                                      @RequestBody AssignVipCodeRequest req) {
        return ResponseEntity.ok(codeService.assign(id, req.organizerId()));
    }

    @PostMapping("/codes/{id}/cancel")
    public ResponseEntity<VipCodeResponse> cancelCode(@PathVariable UUID id) {
        return ResponseEntity.ok(codeService.cancel(id));
    }

    // ── Purchases ─────────────────────────────────────────────────────────

    @GetMapping("/purchases")
    public ResponseEntity<List<VipPurchaseDto>> listPurchases() {
        return ResponseEntity.ok(purchaseService.getAllForAdmin());
    }
}
