package com.rifas.platform.domain.raffle.entity;

import com.rifas.platform.domain.reservation.entity.Reservation;
import com.rifas.platform.shared.enums.NumberStatus;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "raffle_numbers",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_raffle_number",
                columnNames = {"raffle_id", "number"}
        ),
        indexes = {
                @Index(name = "idx_rn_raffle_status",  columnList = "raffle_id, status"),
                @Index(name = "idx_rn_raffle_number",  columnList = "raffle_id, number"),
                @Index(name = "idx_rn_expires",        columnList = "expires_at")
        })
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class RaffleNumber {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "raffle_id", nullable = false)
    private Raffle raffle;

    @Column(nullable = false)
    private Integer number;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private NumberStatus status = NumberStatus.AVAILABLE;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reservation_id")
    private Reservation reservation;

    private LocalDateTime reservedAt;
    private LocalDateTime paidAt;
    private LocalDateTime expiresAt;

    @Version
    private Long version;
}
