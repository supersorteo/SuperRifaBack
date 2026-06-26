package com.rifas.platform.domain.admin.controller;

import com.rifas.platform.domain.vip.dto.AssignVipCodeRequest;
import com.rifas.platform.domain.vip.dto.CreateVipCodeBatchRequest;
import com.rifas.platform.domain.vip.dto.CreateVipCodeRequest;
import com.rifas.platform.domain.vip.dto.OrganizerVipSummaryDto;
import com.rifas.platform.domain.vip.dto.VipCodeResponse;
import com.rifas.platform.domain.vip.dto.VipPackageDto;
import com.rifas.platform.domain.vip.dto.VipPackageRequest;
import com.rifas.platform.domain.vip.dto.VipPurchaseDto;
import com.rifas.platform.domain.vip.service.VipCodeService;
import com.rifas.platform.domain.vip.service.VipPackageService;
import com.rifas.platform.domain.vip.service.VipPurchaseService;
import com.rifas.platform.shared.security.UserDetailsImpl;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/admin/vip")
@RequiredArgsConstructor
public class AdminVipController {

    private final VipPackageService packageService;
    private final VipCodeService codeService;
    private final VipPurchaseService purchaseService;

    @GetMapping("/packages")
    public ResponseEntity<List<VipPackageDto>> listPackages() {
        return ResponseEntity.ok(packageService.findAll());
    }

    @PostMapping("/packages")
    public ResponseEntity<VipPackageDto> createPackage(@Valid @RequestBody VipPackageRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(packageService.create(req));
    }

    @PutMapping("/packages/{id}")
    public ResponseEntity<VipPackageDto> updatePackage(
            @PathVariable UUID id,
            @Valid @RequestBody VipPackageRequest req
    ) {
        return ResponseEntity.ok(packageService.update(id, req));
    }

    @PatchMapping("/packages/{id}/toggle")
    public ResponseEntity<VipPackageDto> togglePackage(@PathVariable UUID id) {
        return ResponseEntity.ok(packageService.toggleActive(id));
    }

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

    @PostMapping("/codes/batch")
    public ResponseEntity<List<VipCodeResponse>> createCodeBatch(@Valid @RequestBody CreateVipCodeBatchRequest req) {
        UUID adminId = ((UserDetailsImpl) SecurityContextHolder
                .getContext().getAuthentication().getPrincipal()).getId();
        return ResponseEntity.status(HttpStatus.CREATED).body(codeService.createManualBatch(req, adminId));
    }

    @PostMapping("/codes/{id}/assign")
    public ResponseEntity<VipCodeResponse> assignCode(
            @PathVariable UUID id,
            @RequestBody AssignVipCodeRequest req
    ) {
        return ResponseEntity.ok(codeService.assign(id, req.organizerId()));
    }

    @PostMapping("/codes/{id}/cancel")
    public ResponseEntity<VipCodeResponse> cancelCode(@PathVariable UUID id) {
        return ResponseEntity.ok(codeService.cancel(id));
    }

    @DeleteMapping("/codes/{id}")
    public ResponseEntity<Void> deleteCode(@PathVariable UUID id) {
        codeService.delete(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/purchases")
    public ResponseEntity<List<VipPurchaseDto>> listPurchases() {
        return ResponseEntity.ok(purchaseService.getAllForAdmin());
    }

    @GetMapping("/organizers/{organizerId}/summary")
    public ResponseEntity<OrganizerVipSummaryDto> organizerSummary(@PathVariable UUID organizerId) {
        return ResponseEntity.ok(purchaseService.getOrganizerVipSummary(organizerId));
    }
}
