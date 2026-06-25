package com.rifas.platform.domain.vip.service;

import com.rifas.platform.domain.organizer.entity.OrganizerProfile;
import com.rifas.platform.domain.organizer.repository.OrganizerProfileRepository;
import com.rifas.platform.domain.vip.dto.VipCodeResponse;
import com.rifas.platform.domain.vip.dto.CreateVipCodeRequest;
import com.rifas.platform.domain.vip.entity.VipCode;
import com.rifas.platform.domain.vip.entity.VipPackage;
import com.rifas.platform.domain.vip.repository.VipCodeRepository;
import com.rifas.platform.domain.vip.repository.VipPackageRepository;
import com.rifas.platform.shared.audit.service.AuditService;
import com.rifas.platform.shared.enums.VipCodeSource;
import com.rifas.platform.shared.enums.VipCodeStatus;
import com.rifas.platform.shared.exception.BusinessException;
import com.rifas.platform.shared.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class VipCodeService {

    private final VipCodeRepository     codeRepository;
    private final VipPackageRepository  packageRepository;
    private final OrganizerProfileRepository organizerProfileRepository;
    private final OrganizerQuotaService quotaService;
    private final AuditService auditService;

    // ── Admin operations ──────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<VipCodeResponse> findAll() {
        return codeRepository.findAll().stream()
                .map(this::toResponse).toList();
    }

    @Transactional
    public VipCodeResponse createManual(CreateVipCodeRequest req, UUID adminUserId) {
        VipPackage pkg = packageRepository.findById(req.packageId())
                .orElseThrow(() -> new ResourceNotFoundException("Paquete VIP no encontrado"));

        OrganizerProfile assignedTo = null;
        if (req.organizerId() != null) {
            assignedTo = organizerProfileRepository.findById(req.organizerId())
                    .orElseThrow(() -> new ResourceNotFoundException("Organizer no encontrado"));
        }

        VipCode code = VipCode.builder()
                .code(generateUniqueCode())
                .vipPackage(pkg)
                .raffleQuantity(pkg.getRaffleQuantity())
                .price(pkg.getPrice())
                .source(VipCodeSource.MANUAL_ADMIN)
                .status(assignedTo != null ? VipCodeStatus.ASSIGNED : VipCodeStatus.GENERATED)
                .assignedOrganizer(assignedTo)
                .generatedByAdminUserId(adminUserId)
                .build();

        VipCode saved = codeRepository.save(code);
        auditService.log("VIP_CODE_GENERATED", "VipCode", saved.getId(), null,
                Map.of("code", saved.getCode(), "package", pkg.getName(),
                       "assignedTo", assignedTo != null ? assignedTo.getId().toString() : "none"));
        return toResponse(saved);
    }

    @Transactional
    public VipCodeResponse assign(UUID codeId, UUID organizerId) {
        VipCode code = findCodeById(codeId);
        if (code.getStatus() != VipCodeStatus.GENERATED) {
            throw new BusinessException("Solo se pueden asignar códigos en estado GENERATED");
        }
        OrganizerProfile organizer = organizerProfileRepository.findById(organizerId)
                .orElseThrow(() -> new ResourceNotFoundException("Organizer no encontrado"));
        code.setAssignedOrganizer(organizer);
        code.setStatus(VipCodeStatus.ASSIGNED);
        VipCode saved = codeRepository.save(code);
        auditService.log("VIP_CODE_ASSIGNED", "VipCode", saved.getId(), null,
                Map.of("code", saved.getCode(), "organizerId", organizer.getId().toString()));
        return toResponse(saved);
    }

    @Transactional
    public VipCodeResponse cancel(UUID codeId) {
        VipCode code = findCodeById(codeId);
        if (code.getStatus() == VipCodeStatus.REDEEMED) {
            throw new BusinessException("No se puede cancelar un código ya canjeado");
        }
        if (code.getStatus() == VipCodeStatus.CANCELLED) {
            throw new BusinessException("El código ya está cancelado");
        }
        code.setStatus(VipCodeStatus.CANCELLED);
        return toResponse(codeRepository.save(code));
    }

    // ── Organizer operations ──────────────────────────────────────────────

    @Transactional
    public VipCodeResponse redeem(String codeString, OrganizerProfile organizer) {
        VipCode code = codeRepository.findByCode(codeString.trim().toUpperCase())
                .orElseThrow(() -> new BusinessException("Código inválido o no existe"));

        if (code.getStatus() == VipCodeStatus.REDEEMED) {
            throw new BusinessException("Este código ya fue utilizado");
        }
        if (code.getStatus() == VipCodeStatus.CANCELLED) {
            throw new BusinessException("Este código fue cancelado y no es válido");
        }
        if (code.getStatus() == VipCodeStatus.EXPIRED) {
            throw new BusinessException("Este código ha expirado");
        }
        if (code.getAssignedOrganizer() != null
                && !code.getAssignedOrganizer().getId().equals(organizer.getId())) {
            throw new BusinessException("Este código fue asignado a otro organizador");
        }

        code.setStatus(VipCodeStatus.REDEEMED);
        code.setRedeemedByOrganizer(organizer);
        code.setRedeemedAt(LocalDateTime.now());
        codeRepository.save(code);

        quotaService.creditVipQuota(organizer.getId(), code.getRaffleQuantity(), code.getCode());
        auditService.log("VIP_CODE_REDEEMED", "VipCode", code.getId(), null,
                Map.of("code", code.getCode(), "organizerId", organizer.getId().toString(),
                       "quantity", code.getRaffleQuantity()));

        return toResponse(code);
    }

    // ── Helpers ───────────────────────────────────────────────────────────

    private VipCode findCodeById(UUID id) {
        return codeRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Código VIP no encontrado"));
    }

    private String generateUniqueCode() {
        String chars = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
        String code;
        do {
            StringBuilder sb = new StringBuilder("VIP-");
            for (int i = 0; i < 4; i++) sb.append(chars.charAt((int)(Math.random() * chars.length())));
            sb.append("-");
            for (int i = 0; i < 4; i++) sb.append(chars.charAt((int)(Math.random() * chars.length())));
            code = sb.toString();
        } while (codeRepository.existsByCode(code));
        return code;
    }

    private VipCodeResponse toResponse(VipCode c) {
        String assignedEmail = c.getAssignedOrganizer() != null ? c.getAssignedOrganizer().getUser().getEmail() : null;
        String assignedName  = c.getAssignedOrganizer() != null ? c.getAssignedOrganizer().getUser().getFullName() : null;
        String redeemedEmail = c.getRedeemedByOrganizer() != null ? c.getRedeemedByOrganizer().getUser().getEmail() : null;
        String redeemedName  = c.getRedeemedByOrganizer() != null ? c.getRedeemedByOrganizer().getUser().getFullName() : null;
        return new VipCodeResponse(
                c.getId(), c.getCode(),
                c.getVipPackage().getName(), c.getRaffleQuantity(), c.getPrice(),
                c.getSource().name(), c.getStatus().name(),
                assignedEmail, assignedName,
                redeemedEmail, redeemedName,
                c.getRedeemedAt(), c.getCreatedAt()
        );
    }
}
