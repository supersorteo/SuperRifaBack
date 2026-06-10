package com.rifas.platform.domain.execution.entity;

import com.rifas.platform.domain.raffle.entity.Raffle;
import com.rifas.platform.shared.enums.DrawMethod;
import com.rifas.platform.shared.enums.ExecutionStatus;
import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "raffle_executions")
@EntityListeners(AuditingEntityListener.class)
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class RaffleExecution {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "raffle_id", unique = true, nullable = false)
    private Raffle raffle;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private DrawMethod method;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private ExecutionStatus status = ExecutionStatus.PENDING;

    private Integer drawnNumber;
    private Integer eligibleNumbersCount;

    @Column(columnDefinition = "TEXT")
    private String eligibleNumbersSnapshot;

    private UUID executedByUserId;
    private LocalDateTime executedAt;
    private LocalDateTime completedAt;

    @Column(columnDefinition = "TEXT")
    private String notes;

    @OneToOne(mappedBy = "execution", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private ExternalDrawExecution externalExecution;

    @CreatedDate @Column(updatable = false)
    private LocalDateTime createdAt;
}
