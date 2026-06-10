package com.rifas.platform.domain.execution.entity;

import com.rifas.platform.shared.enums.ValidationStatus;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "external_draw_executions")
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class ExternalDrawExecution {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "execution_id", unique = true, nullable = false)
    private RaffleExecution execution;

    @Column(length = 100)
    private String providerName;

    private String externalExecutionId;
    private LocalDateTime queriedAt;

    @Column(columnDefinition = "TEXT")
    private String rawResponse;

    @Column(columnDefinition = "TEXT")
    private String evidenceSnapshot;

    private Integer returnedNumber;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    @Builder.Default
    private ValidationStatus validationStatus = ValidationStatus.PENDING;

    @Column(columnDefinition = "TEXT")
    private String validationNotes;

    @Builder.Default
    private Integer retryCount = 0;
}
