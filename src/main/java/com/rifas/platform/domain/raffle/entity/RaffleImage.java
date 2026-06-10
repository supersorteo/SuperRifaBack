package com.rifas.platform.domain.raffle.entity;

import jakarta.persistence.*;
import lombok.*;

import java.util.UUID;

@Entity
@Table(name = "raffle_images")
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class RaffleImage {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "raffle_id", nullable = false)
    private Raffle raffle;

    @Column(nullable = false)
    private String url;

    private String publicId;
    private String altText;

    @Builder.Default
    private Integer displayOrder = 0;

    @Builder.Default
    private boolean coverImage = false;
}
