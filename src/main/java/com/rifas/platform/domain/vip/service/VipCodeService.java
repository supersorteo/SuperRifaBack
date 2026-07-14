package com.rifas.platform.domain.vip.service;

import com.rifas.platform.domain.admin.service.AdminOrganizerService;
import com.rifas.platform.domain.organizer.entity.OrganizerProfile;
import com.rifas.platform.domain.organizer.repository.OrganizerProfileRepository;
import com.rifas.platform.domain.vip.dto.CreateVipCodeBatchRequest;
import com.rifas.platform.domain.vip.dto.CreateVipCodeRequest;
import com.rifas.platform.domain.vip.dto.VipCodeResponse;
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
import java.util.stream.IntStream;

@Service
@RequiredArgsConstructor
public class VipCodeService {

    private final VipCodeRepository codeRepository;
    private final VipPackageRepository packageRepository;
    private final OrganizerProfileRepository organizerProfileRepository;
    private final OrganizerQuotaService quotaService;
    private final VipCodeGenerator codeGenerator;
    private final AuditService auditService;
    private final AdminOrganizerService adminOrganizerService;

    @Transactional(readOnly = true)
    public List<VipCodeResponse> findAll() {
        return codeRepository.findAll().stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public VipCodeResponse createManual(CreateVipCodeRequest req, UUID adminUserId) {
        VipPackage pkg = findPackage(req.packageId());
        OrganizerProfile assignedTo = findAssignedOrganizer(req.organizerId());

        VipCode code = buildManualCode(pkg, assignedTo, adminUserId);
        if (assignedTo != null) {
            code.setStatus(VipCodeStatus.REDEEMED);
            code.setRedeemedByOrganizer(assignedTo);
            code.setRedeemedAt(LocalDateTime.now());
        }
        VipCode saved = codeRepository.save(code);

        if (assignedTo != null) {
            quotaService.creditVipQuota(assignedTo.getId(), pkg.getRaffleQuantity(), saved.getCode());
        }

        auditService.log("VIP_CODE_GENERATED", "VipCode", saved.getId(), null,
                Map.of(
                        "code", saved.getCode(),
                        "package", pkg.getName(),
                        "quantity", pkg.getRaffleQuantity(),
                        "assignedTo", assignedTo != null ? assignedTo.getId().toString() : "none"
                ));
        return toResponse(saved);
    }

    @Transactional
    public List<VipCodeResponse> createManualBatch(CreateVipCodeBatchRequest req, UUID adminUserId) {
        VipPackage pkg = findPackage(req.packageId());
        OrganizerProfile assignedTo = findAssignedOrganizer(req.organizerId());

        List<VipCode> saved = codeRepository.saveAll(
                IntStream.range(0, req.quantity())
                        .mapToObj(i -> buildManualCode(pkg, assignedTo, adminUserId))
                        .toList()
        );

        auditService.log("VIP_CODE_BATCH_GENERATED", "VipCode", null, null,
                Map.of(
                        "package", pkg.getName(),
                        "raffleQuantity", pkg.getRaffleQuantity(),
                        "batchSize", req.quantity(),
                        "assignedTo", assignedTo != null ? assignedTo.getId().toString() : "none"
                ));

        return saved.stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public VipCodeResponse assign(UUID codeId, UUID organizerId) {
        VipCode code = findCodeById(codeId);
        if (code.getStatus() != VipCodeStatus.GENERATED) {
            throw new BusinessException("Solo se pueden asignar codigos en estado GENERATED");
        }

        OrganizerProfile organizer = organizerProfileRepository.findById(organizerId)
                .orElseThrow(() -> new ResourceNotFoundException("Organizer no encontrado"));

        code.setAssignedOrganizer(organizer);
        code.setStatus(VipCodeStatus.REDEEMED);
        code.setRedeemedByOrganizer(organizer);
        code.setRedeemedAt(LocalDateTime.now());
        VipCode saved = codeRepository.save(code);

        quotaService.creditVipQuota(organizer.getId(), code.getRaffleQuantity(), code.getCode());

        auditService.log("VIP_CODE_ASSIGNED", "VipCode", saved.getId(), null,
                Map.of("code", saved.getCode(), "organizerId", organizer.getId().toString(),
                       "quantity", code.getRaffleQuantity()));
        return toResponse(saved);
    }

    @Transactional
    public VipCodeResponse cancel(UUID codeId) {
        VipCode code = findCodeById(codeId);
        if (code.getStatus() == VipCodeStatus.REDEEMED) {
            throw new BusinessException("No se puede cancelar un codigo ya canjeado");
        }
        if (code.getStatus() == VipCodeStatus.CANCELLED) {
            throw new BusinessException("El codigo ya esta cancelado");
        }

        code.setStatus(VipCodeStatus.CANCELLED);
        return toResponse(codeRepository.save(code));
    } 

    @Transactional
    public void delete(UUID codeId) {
        VipCode code = findCodeById(codeId);

        OrganizerProfile redeemed = code.getRedeemedByOrganizer();
        if (redeemed != null) {
            // Deleting a redeemed code cascades: removes the organizer and all their data
            adminOrganizerService.deleteOrganizer(redeemed.getId());
            return; // code is deleted as part of the organizer cascade
        }

        String codeName = code.getCode();
        codeRepository.delete(code);
        auditService.log("VIP_CODE_DELETED", "VipCode", codeId, null,
                Map.of("code", codeName));
    }

    @Transactional
    public void redeem(String codeString, OrganizerProfile organizer) {
        VipCode code = codeRepository.findByCode(codeString.trim().toUpperCase())
                .orElseThrow(() -> new BusinessException("Codigo invalido o no existe"));

        if (code.getStatus() == VipCodeStatus.REDEEMED) {
            throw new BusinessException("Este codigo ya fue utilizado");
        }
        if (code.getStatus() == VipCodeStatus.CANCELLED) {
            throw new BusinessException("Este codigo fue cancelado y no es valido");
        }
        if (code.getStatus() == VipCodeStatus.EXPIRED) {
            throw new BusinessException("Este codigo ha expirado");
        }
        if (code.getAssignedOrganizer() != null
                && !code.getAssignedOrganizer().getId().equals(organizer.getId())) {
            throw new BusinessException("Este codigo fue asignado a otro organizador");
        }

        code.setStatus(VipCodeStatus.REDEEMED);
        code.setRedeemedByOrganizer(organizer);
        code.setRedeemedAt(LocalDateTime.now());
        codeRepository.save(code);

        quotaService.creditVipQuota(organizer.getId(), code.getRaffleQuantity(), code.getCode());
        auditService.log("VIP_CODE_REDEEMED", "VipCode", code.getId(), null,
                Map.of(
                        "code", code.getCode(),
                        "organizerId", organizer.getId().toString(),
                        "quantity", code.getRaffleQuantity()
                ));
    }

    private VipPackage findPackage(UUID packageId) {
        return packageRepository.findById(packageId)
                .orElseThrow(() -> new ResourceNotFoundException("Paquete VIP no encontrado"));
    }

    private OrganizerProfile findAssignedOrganizer(UUID organizerId) {
        if (organizerId == null) {
            return null;
        }
        return organizerProfileRepository.findById(organizerId)
                .orElseThrow(() -> new ResourceNotFoundException("Organizer no encontrado"));
    }

    private VipCode findCodeById(UUID id) {
        return codeRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Codigo VIP no encontrado"));
    }

    private VipCode buildManualCode(VipPackage pkg, OrganizerProfile assignedTo, UUID adminUserId) {
        return VipCode.builder()
                .code(codeGenerator.generateUniqueCode(pkg.getRaffleQuantity()))
                .vipPackage(pkg)
                .raffleQuantity(pkg.getRaffleQuantity())
                .price(pkg.getPrice())
                .source(VipCodeSource.MANUAL_ADMIN)
                .status(assignedTo != null ? VipCodeStatus.ASSIGNED : VipCodeStatus.GENERATED)
                .assignedOrganizer(assignedTo)
                .generatedByAdminUserId(adminUserId)
                .build();
    }

    private VipCodeResponse toResponse(VipCode c) {
        String assignedEmail = c.getAssignedOrganizer() != null ? c.getAssignedOrganizer().getUser().getEmail() : null;
        String assignedName = c.getAssignedOrganizer() != null ? c.getAssignedOrganizer().getUser().getFullName() : null;
        String redeemedEmail = c.getRedeemedByOrganizer() != null ? c.getRedeemedByOrganizer().getUser().getEmail() : null;
        String redeemedName = c.getRedeemedByOrganizer() != null ? c.getRedeemedByOrganizer().getUser().getFullName() : null;

        return new VipCodeResponse(
                c.getId(),
                c.getCode(),
                c.getVipPackage().getName(),
                c.getRaffleQuantity(),
                c.getPrice(),
                c.getSource().name(),
                c.getStatus().name(),
                assignedEmail,
                assignedName,
                redeemedEmail,
                redeemedName,
                c.getRedeemedAt(),
                c.getCreatedAt()
        );
    }
}
