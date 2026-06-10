package com.rifas.platform.domain.raffle.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "raffle_prizes")
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class RafflePrize {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "raffle_id", unique = true, nullable = false)
    private Raffle raffle;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(precision = 12, scale = 2)
    private BigDecimal estimatedValue;

    private String brand;
    private String model;
    private String imageUrl;
}
