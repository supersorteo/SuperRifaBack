package com.rifas.platform.domain.vip.entity;

import com.rifas.platform.domain.organizer.entity.OrganizerProfile;
import com.rifas.platform.shared.enums.VipCodeSource;
import com.rifas.platform.shared.enums.VipCodeStatus;
import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "vip_codes", indexes = {
        @Index(name = "idx_vip_codes_code",   columnList = "code",               unique = true),
        @Index(name = "idx_vip_codes_status",  columnList = "status"),
        @Index(name = "idx_vip_codes_assigned", columnList = "assigned_organizer_id")
})
@EntityListeners(AuditingEntityListener.class)
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class VipCode {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    /** Código alfanumérico único visible para el organizer. */
    @Column(nullable = false, unique = true, length = 32)
    private String code;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "vip_package_id", nullable = false)
    private VipPackage vipPackage;

    /** Cupos que otorga este código (snapshot del paquete al momento de generación). */
    @Column(nullable = false)
    private int raffleQuantity;

    /** Precio del paquete al momento de generación (para auditoría). */
    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal price;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private VipCodeSource source;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private VipCodeStatus status = VipCodeStatus.GENERATED;

    /** Si fue pre-asignado a un organizer específico por el admin (puede ser null). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assigned_organizer_id")
    private OrganizerProfile assignedOrganizer;

    /** Organizer que efectivamente canjeó el código. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "redeemed_by_organizer_id")
    private OrganizerProfile redeemedByOrganizer;

    /** Admin que generó el código manualmente (null si fue MP). */
    @Column(name = "generated_by_admin_user_id")
    private UUID generatedByAdminUserId;

    /** ID de pago externo (preferenceId de MP o referencia de auditoría). */
    @Column(length = 200)
    private String paymentReference;

    @CreatedDate @Column(updatable = false) private LocalDateTime createdAt;
    private LocalDateTime redeemedAt;
}
