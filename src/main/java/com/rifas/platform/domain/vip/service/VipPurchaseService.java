package com.rifas.platform.domain.vip.service;

import com.rifas.platform.domain.organizer.entity.OrganizerProfile;
import com.rifas.platform.domain.vip.dto.CreateVipPreferenceRequest;
import com.rifas.platform.domain.vip.dto.VipPreferenceResponse;
import com.rifas.platform.domain.vip.dto.VipPurchaseDto;
import com.rifas.platform.domain.vip.entity.VipCode;
import com.rifas.platform.domain.vip.entity.VipPackage;
import com.rifas.platform.domain.vip.entity.VipPurchase;
import com.rifas.platform.domain.vip.repository.VipCodeRepository;
import com.rifas.platform.domain.vip.repository.VipPackageRepository;
import com.rifas.platform.domain.vip.repository.VipPurchaseRepository;
import com.rifas.platform.shared.enums.VipCodeSource;
import com.rifas.platform.shared.enums.VipCodeStatus;
import com.rifas.platform.shared.enums.VipPurchaseStatus;
import com.rifas.platform.shared.exception.BusinessException;
import com.rifas.platform.shared.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class VipPurchaseService {

    private final VipPurchaseRepository purchaseRepository;
    private final VipPackageRepository  packageRepository;
    private final VipCodeRepository     codeRepository;
    private final OrganizerQuotaService quotaService;
    private final VipMercadoPagoService mpService;

    // ── Organizer: crear preferencia ─────────────────────────────────────

    @Transactional
    public VipPreferenceResponse createPreference(CreateVipPreferenceRequest req,
                                                   OrganizerProfile organizer) {
        VipPackage pkg = packageRepository.findById(req.vipPackageId())
                .orElseThrow(() -> new ResourceNotFoundException("Paquete VIP no encontrado"));
        if (!pkg.isActive()) {
            throw new BusinessException("El paquete VIP no está disponible");
        }

        VipPurchase purchase = VipPurchase.builder()
                .organizer(organizer)
                .vipPackage(pkg)
                .amount(pkg.getPrice())
                .currency(pkg.getCurrency())
                .status(VipPurchaseStatus.PENDING)
                .build();
        purchase = purchaseRepository.save(purchase);

        VipMercadoPagoService.PreferenceResult result = mpService.createPreference(purchase);
        purchase.setExternalPreferenceId(result.preferenceId());
        purchaseRepository.save(purchase);

        return new VipPreferenceResponse(purchase.getId(), result.preferenceId(), result.initPoint());
    }

    // ── Webhook: aprobar compra (idempotente) ─────────────────────────────

    @Transactional
    public void processApproval(String externalPaymentId, String externalReference) {
        // Idempotencia: mismo paymentId de MP no se procesa dos veces
        if (purchaseRepository.existsByExternalPaymentId(externalPaymentId)) {
            log.info("[VIP-WEBHOOK] Pago {} ya procesado — ignorando", externalPaymentId);
            return;
        }

        UUID purchaseId;
        try {
            purchaseId = UUID.fromString(externalReference);
        } catch (IllegalArgumentException ex) {
            log.warn("[VIP-WEBHOOK] externalReference no es UUID válido: {}", externalReference);
            return;
        }

        VipPurchase purchase = purchaseRepository.findById(purchaseId).orElse(null);
        if (purchase == null) {
            log.warn("[VIP-WEBHOOK] VipPurchase {} no encontrada para mpPaymentId={}", purchaseId, externalPaymentId);
            return;
        }
        if (purchase.getStatus() == VipPurchaseStatus.APPROVED) {
            log.info("[VIP-WEBHOOK] VipPurchase {} ya aprobada — ignorando (mpPaymentId={})", purchaseId, externalPaymentId);
            purchase.setExternalPaymentId(externalPaymentId);
            purchaseRepository.save(purchase);
            return;
        }

        // Generar VipCode interno con fuente MERCADO_PAGO
        VipCode code = VipCode.builder()
                .code(generateUniqueCode())
                .vipPackage(purchase.getVipPackage())
                .raffleQuantity(purchase.getVipPackage().getRaffleQuantity())
                .price(purchase.getAmount())
                .source(VipCodeSource.MERCADO_PAGO)
                .status(VipCodeStatus.REDEEMED)
                .redeemedByOrganizer(purchase.getOrganizer())
                .redeemedAt(LocalDateTime.now())
                .paymentReference(externalPaymentId)
                .build();
        code = codeRepository.save(code);

        purchase.setStatus(VipPurchaseStatus.APPROVED);
        purchase.setExternalPaymentId(externalPaymentId);
        purchase.setGeneratedCode(code);
        purchase.setCreditedAt(LocalDateTime.now());
        purchaseRepository.save(purchase);

        quotaService.creditVipQuota(
                purchase.getOrganizer().getId(),
                purchase.getVipPackage().getRaffleQuantity(),
                "MP:" + externalPaymentId
        );

        log.info("[VIP-WEBHOOK] Compra VIP {} aprobada. Organizer {} acredita {} cupos.",
                purchaseId, purchase.getOrganizer().getId(),
                purchase.getVipPackage().getRaffleQuantity());
    }

    // ── Historial ─────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<VipPurchaseDto> getHistoryForOrganizer(UUID organizerId) {
        return purchaseRepository.findByOrganizerWithPackage(organizerId)
                .stream().map(p -> toDto(p, false)).toList();
    }

    @Transactional(readOnly = true)
    public List<VipPurchaseDto> getAllForAdmin() {
        return purchaseRepository.findAllWithDetails()
                .stream().map(p -> toDto(p, true)).toList();
    }

    // ── Helpers ───────────────────────────────────────────────────────────

    private VipPurchaseDto toDto(VipPurchase p, boolean includeOrganizer) {
        return new VipPurchaseDto(
                p.getId(),
                p.getVipPackage().getName(),
                p.getVipPackage().getRaffleQuantity(),
                p.getAmount(),
                p.getCurrency(),
                p.getStatus().name(),
                p.getCreditedAt() != null,
                p.getCreditedAt(),
                p.getCreatedAt(),
                includeOrganizer ? p.getOrganizer().getUser().getEmail() : null,
                includeOrganizer ? p.getOrganizer().getUser().getFullName() : null
        );
    }

    private String generateUniqueCode() {
        String chars = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
        String code;
        do {
            StringBuilder sb = new StringBuilder("VIP-");
            for (int i = 0; i < 4; i++) sb.append(chars.charAt((int) (Math.random() * chars.length())));
            sb.append("-");
            for (int i = 0; i < 4; i++) sb.append(chars.charAt((int) (Math.random() * chars.length())));
            code = sb.toString();
        } while (codeRepository.existsByCode(code));
        return code;
    }
}
