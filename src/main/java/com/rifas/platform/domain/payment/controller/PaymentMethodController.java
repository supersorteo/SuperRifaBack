package com.rifas.platform.domain.payment.controller;

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

    public record PaymentMethodRequest(
            @NotNull PaymentMethodType type,
            @NotBlank @Size(max = 100) String displayName,
            String alias,
            String cbu,
            String cvu,
            String accountHolder,
            @Size(max = 500) String instructions,
            boolean publicVisible,
            Integer displayOrder
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

    private OrganizerProfile currentOrganizer() {
        UserDetailsImpl ud = (UserDetailsImpl) SecurityContextHolder
                .getContext().getAuthentication().getPrincipal();
        return organizerProfileRepository.findByUserId(ud.getId())
                .orElseThrow(() -> new BusinessException("Perfil de organizador no encontrado"));
    }
}
