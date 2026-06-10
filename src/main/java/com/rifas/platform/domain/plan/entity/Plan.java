package com.rifas.platform.domain.plan.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "plans")
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class Plan {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, length = 50)
    private String name;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(nullable = false, precision = 10, scale = 2)
    @Builder.Default
    private BigDecimal monthlyPrice = BigDecimal.ZERO;

    @Column(precision = 10, scale = 2)
    private BigDecimal annualPrice;

    private Integer maxActiveRaffles;
    private Integer maxRafflesPerMonth;
    private Integer maxNumbersPerRaffle;

    @Builder.Default private boolean externalDrawEnabled    = false;
    @Builder.Default private boolean customBrandingEnabled  = false;
    @Builder.Default private boolean advancedReportsEnabled = false;
    @Builder.Default private boolean active                 = true;
    @Builder.Default private Integer displayOrder           = 0;
}
