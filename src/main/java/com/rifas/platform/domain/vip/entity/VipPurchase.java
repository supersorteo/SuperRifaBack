package com.rifas.platform.domain.vip.entity;

import com.rifas.platform.domain.organizer.entity.OrganizerProfile;
import com.rifas.platform.shared.enums.VipPurchaseProvider;
import com.rifas.platform.shared.enums.VipPurchaseStatus;
import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "vip_purchases", indexes = {
        @Index(name = "idx_vip_purchases_organizer", columnList = "organizer_id"),
        @Index(name = "idx_vip_purchases_ext_payment", columnList = "external_payment_id", unique = true),
        @Index(name = "idx_vip_purchases_status",    columnList = "status")
})
@EntityListeners(AuditingEntityListener.class)
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class VipPurchase {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "organizer_id", nullable = false)
    private OrganizerProfile organizer;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "vip_package_id", nullable = false)
    private VipPackage vipPackage;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false, length = 10)
    @Builder.Default
    private String currency = "USD";

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private VipPurchaseProvider provider = VipPurchaseProvider.MERCADO_PAGO;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private VipPurchaseStatus status = VipPurchaseStatus.PENDING;

    /** ID del pago en MP (usado para idempotencia en webhook). */
    @Column(name = "external_payment_id", unique = true, length = 100)
    private String externalPaymentId;

    /** ID de la preference MP generada al iniciar el pago. */
    @Column(name = "external_preference_id", length = 100)
    private String externalPreferenceId;

    /** VipCode interno generado para auditoría cuando se aprueba la compra. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "generated_code_id")
    private VipCode generatedCode;

    /** Timestamp en que se acreditaron los cupos. */
    private LocalDateTime creditedAt;

    @CreatedDate @Column(updatable = false) private LocalDateTime createdAt;
    @LastModifiedDate private LocalDateTime updatedAt;
}
