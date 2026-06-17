package com.rifas.platform.domain.payment.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rifas.platform.domain.organizer.entity.OrganizerProfile;
import com.rifas.platform.domain.organizer.repository.OrganizerProfileRepository;
import com.rifas.platform.domain.payment.entity.PaymentMethod;
import com.rifas.platform.domain.payment.repository.PaymentMethodRepository;
import com.rifas.platform.shared.enums.PaymentMethodType;
import com.rifas.platform.shared.exception.BusinessException;
import com.rifas.platform.shared.exception.ResourceNotFoundException;
import com.rifas.platform.shared.security.UserDetailsImpl;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/organizer/payment-methods")
@RequiredArgsConstructor
public class PaymentMethodController {

    private final PaymentMethodRepository paymentMethodRepository;
    private final OrganizerProfileRepository organizerProfileRepository;
    private final ObjectMapper objectMapper;

    public record PaymentMethodRequest(
            @NotNull PaymentMethodType type,
            @NotBlank @Size(max = 100) String displayName,
            String alias,
            String cbu,
            String cvu,
            String accountHolder,
            @Size(max = 500) String instructions,
            boolean publicVisible,
            Integer displayOrder,
            /** Solo para type=MERCADO_PAGO: access_token personal del organizer */
            String mpAccessToken
    ) {}

    @GetMapping
    public ResponseEntity<List<PaymentMethod>> list() {
        return ResponseEntity.ok(
                paymentMethodRepository.findByOrganizerIdAndActiveTrueOrderByDisplayOrderAsc(currentOrganizer().getId()));
    }

    @PostMapping
    public ResponseEntity<PaymentMethod> create(@Valid @RequestBody PaymentMethodRequest req) {
        OrganizerProfile org = currentOrganizer();
        PaymentMethod pm = PaymentMethod.builder()
                .organizer(org)
                .type(req.type())
                .displayName(req.displayName())
                .alias(req.alias())
                .cbu(req.cbu())
                .cvu(req.cvu())
                .accountHolder(req.accountHolder())
                .instructions(req.instructions())
                .integrationMetadata(buildMetadata(req))
                .active(true)
                .publicVisible(req.publicVisible())
                .displayOrder(req.displayOrder() != null ? req.displayOrder() : 0)
                .build();
        return ResponseEntity.status(HttpStatus.CREATED).body(paymentMethodRepository.save(pm));
    }

    @PutMapping("/{id}")
    public ResponseEntity<PaymentMethod> update(@PathVariable UUID id,
                                                 @Valid @RequestBody PaymentMethodRequest req) {
        PaymentMethod pm = findOwned(id);
        pm.setType(req.type());
        pm.setDisplayName(req.displayName());
        pm.setAlias(req.alias());
        pm.setCbu(req.cbu());
        pm.setCvu(req.cvu());
        pm.setAccountHolder(req.accountHolder());
        pm.setInstructions(req.instructions());
        // Solo actualiza el token si se envió uno nuevo; si no, conserva el existente
        String newMeta = buildMetadata(req);
        if (newMeta != null) pm.setIntegrationMetadata(newMeta);
        if (req.type() != PaymentMethodType.MERCADO_PAGO) pm.setIntegrationMetadata(null);
        pm.setPublicVisible(req.publicVisible());
        if (req.displayOrder() != null) pm.setDisplayOrder(req.displayOrder());
        return ResponseEntity.ok(paymentMethodRepository.save(pm));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deactivate(@PathVariable UUID id) {
        PaymentMethod pm = findOwned(id);
        pm.setActive(false);
        paymentMethodRepository.save(pm);
        return ResponseEntity.noContent().build();
    }

    private PaymentMethod findOwned(UUID id) {
        PaymentMethod pm = paymentMethodRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Método de pago no encontrado"));
        if (!pm.getOrganizer().getId().equals(currentOrganizer().getId())) {
            throw new BusinessException("Sin permisos sobre este método de pago");
        }
        return pm;
    }

    private String buildMetadata(PaymentMethodRequest req) {
        if (req.type() != PaymentMethodType.MERCADO_PAGO
                || req.mpAccessToken() == null || req.mpAccessToken().isBlank()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(
                    java.util.Map.of("accessToken", req.mpAccessToken().strip()));
        } catch (JsonProcessingException ex) {
            throw new BusinessException("Error al procesar credenciales de Mercado Pago");
        }
    }

    private OrganizerProfile currentOrganizer() {
        UserDetailsImpl ud = (UserDetailsImpl) SecurityContextHolder
                .getContext().getAuthentication().getPrincipal();
        return organizerProfileRepository.findByUserId(ud.getId())
                .orElseThrow(() -> new BusinessException("Perfil de organizador no encontrado"));
    }
}
