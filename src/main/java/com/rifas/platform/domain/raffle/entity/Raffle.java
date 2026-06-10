package com.rifas.platform.domain.raffle.entity;

import com.rifas.platform.domain.organizer.entity.OrganizerProfile;
import com.rifas.platform.shared.enums.*;
import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "raffles", indexes = {
        @Index(name = "idx_raffle_slug",   columnList = "slug",   unique = true),
        @Index(name = "idx_raffle_org",    columnList = "organizer_id, publication_status"),
        @Index(name = "idx_raffle_draw",   columnList = "draw_date_time, draw_method, operational_status")
})
@EntityListeners(AuditingEntityListener.class)
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class Raffle {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(unique = true, nullable = false, length = 250)
    private String slug;

    @Column(unique = true, length = 30)
    private String internalCode;

    @Column(columnDefinition = "TEXT")
    private String description;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "organizer_id", nullable = false)
    private OrganizerProfile organizer;

    @Column(nullable = false)
    private Integer totalNumbers;

    @Column(nullable = false)
    private Integer rangeStart;

    @Column(nullable = false)
    private Integer rangeEnd;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal pricePerNumber;

    private LocalDateTime drawDateTime;

    @Column(length = 60)
    private String timezone;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    @Builder.Default
    private PublicationStatus publicationStatus = PublicationStatus.DRAFT;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    @Builder.Default
    private OperationalStatus operationalStatus = OperationalStatus.ACTIVE;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private DrawMethod drawMethod = DrawMethod.MANUAL;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private DrawPolicy drawPolicy = DrawPolicy.PAID_ONLY;

    @Column(columnDefinition = "TEXT")
    private String termsAndConditions;

    private Integer winnerNumber;

    // FK referenciada después de crear Reservation para evitar circularidad
    @Column(name = "winner_reservation_id")
    private UUID winnerReservationId;

    private LocalDateTime executedAt;

    @Column(length = 100)
    private String externalProviderConfig;

    @OneToOne(mappedBy = "raffle", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private RafflePrize prize;

    @OneToMany(mappedBy = "raffle", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @OrderBy("displayOrder ASC")
    @Builder.Default
    private List<RaffleImage> images = new ArrayList<>();

    @CreatedDate  @Column(updatable = false) private LocalDateTime createdAt;
    @LastModifiedDate private LocalDateTime updatedAt;
}
