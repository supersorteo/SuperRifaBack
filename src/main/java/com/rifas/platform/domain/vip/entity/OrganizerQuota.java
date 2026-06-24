package com.rifas.platform.domain.vip.entity;

import com.rifas.platform.domain.organizer.entity.OrganizerProfile;
import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "organizer_quotas")
@EntityListeners(AuditingEntityListener.class)
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class OrganizerQuota {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "organizer_id", unique = true, nullable = false)
    private OrganizerProfile organizer;

    /** Cupos gratuitos otorgados (siempre 1 al registrarse). */
    @Builder.Default
    private int freeGranted = 1;

    /** Cupos gratuitos ya consumidos. */
    @Builder.Default
    private int freeConsumed = 0;

    /** Total de cupos VIP acreditados por compras/códigos. */
    @Builder.Default
    private int vipPurchased = 0;

    /** Cupos VIP ya consumidos. */
    @Builder.Default
    private int vipConsumed = 0;

    /**
     * Fuente de verdad: cupos restantes para crear rifas.
     * = (freeGranted - freeConsumed) + (vipPurchased - vipConsumed)
     * Se gestiona explícitamente (no calculado) para soportar locks y auditoría.
     */
    @Builder.Default
    private int availableRaffles = 1;

    @CreatedDate @Column(updatable = false) private LocalDateTime createdAt;
    @LastModifiedDate private LocalDateTime updatedAt;
}
