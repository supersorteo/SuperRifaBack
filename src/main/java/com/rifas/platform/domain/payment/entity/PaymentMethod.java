package com.rifas.platform.domain.payment.entity;

import com.rifas.platform.domain.organizer.entity.OrganizerProfile;
import com.rifas.platform.shared.enums.PaymentMethodType;
import jakarta.persistence.*;
import lombok.*;

import java.util.UUID;

@Entity
@Table(name = "payment_methods")
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class PaymentMethod {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "organizer_id", nullable = false)
    private OrganizerProfile organizer;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private PaymentMethodType type;

    @Column(nullable = false, length = 100)
    private String displayName;

    @Column(length = 50)
    private String provider;

    @Column(length = 50)
    private String alias;

    @Column(length = 22)
    private String cbu;

    @Column(length = 22)
    private String cvu;

    @Column(length = 150)
    private String accountHolder;

    @Column(columnDefinition = "TEXT")
    private String instructions;

    @Builder.Default private boolean active        = true;
    @Builder.Default private boolean publicVisible = true;
    @Builder.Default private Integer displayOrder  = 0;

    @Column(columnDefinition = "TEXT")
    @com.fasterxml.jackson.annotation.JsonIgnore
    private String integrationMetadata;
}
