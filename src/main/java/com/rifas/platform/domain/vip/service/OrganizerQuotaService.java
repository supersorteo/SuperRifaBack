package com.rifas.platform.domain.vip.service;

import com.rifas.platform.domain.organizer.entity.OrganizerProfile;
import com.rifas.platform.domain.plan.repository.SubscriptionRepository;
import com.rifas.platform.domain.raffle.entity.Raffle;
import com.rifas.platform.domain.vip.dto.OrganizerQuotaSummaryDto;
import com.rifas.platform.domain.vip.entity.OrganizerQuota;
import com.rifas.platform.domain.vip.repository.OrganizerQuotaRepository;
import com.rifas.platform.shared.enums.PublicationStatus;
import com.rifas.platform.shared.exception.BusinessException;
import com.rifas.platform.shared.exception.PlanLimitExceededException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class OrganizerQuotaService {

    private final OrganizerQuotaRepository quotaRepository;
    private final SubscriptionRepository subscriptionRepository;

    /**
     * Crea la cuota inicial para un organizer recién registrado.
     * Idempotente: no hace nada si ya existe.
     */
    @Transactional
    public void initializeForOrganizer(OrganizerProfile organizer) {
        if (quotaRepository.findByOrganizerId(organizer.getId()).isPresent()) {
            return;
        }
        quotaRepository.save(OrganizerQuota.builder()
                .organizer(organizer)
                .freeGranted(1)
                .freeConsumed(0)
                .vipPurchased(0)
                .vipConsumed(0)
                .availableRaffles(1)
                .build());
    }

    /**
     * Obtiene la cuota del organizer, inicializándola si no existe
     * (caso de organizers registrados antes de esta versión).
     */
    @Transactional
    public OrganizerQuota getOrCreateQuota(OrganizerProfile organizer) {
        return quotaRepository.findByOrganizerId(organizer.getId())
                .orElseGet(() -> quotaRepository.save(OrganizerQuota.builder()
                        .organizer(organizer)
                        .freeGranted(1)
                        .freeConsumed(0)
                        .vipPurchased(0)
                        .vipConsumed(0)
                        .availableRaffles(1)
                        .build()));
    }

    /** Devuelve el resumen de cuota enriquecido con el nombre del plan activo. */
    @Transactional(readOnly = true)
    public OrganizerQuotaSummaryDto getQuotaSummary(UUID organizerId) {
        OrganizerQuota quota = quotaRepository.findByOrganizerId(organizerId)
                .orElseThrow(() -> new BusinessException("Cuota no encontrada para el organizer"));

        String planName = subscriptionRepository.findByOrganizerId(organizerId)
                .map(sub -> sub.getPlan().getName())
                .orElse("FREE");

        boolean isVip = !"FREE".equalsIgnoreCase(planName) || quota.getVipPurchased() > 0;

        return new OrganizerQuotaSummaryDto(
                planName,
                quota.getFreeGranted(),
                quota.getFreeConsumed(),
                quota.getVipPurchased(),
                quota.getVipConsumed(),
                quota.getAvailableRaffles(),
                isVip,
                quota.getAvailableRaffles() > 0
        );
    }

    /**
     * Como getQuotaSummary pero inicializa la cuota si no existe (organizers legados).
     * Usado por el endpoint de subscription-status.
     */
    @Transactional
    public OrganizerQuotaSummaryDto getSummary(OrganizerProfile organizer) {
        OrganizerQuota quota = getOrCreateQuota(organizer);
        String planName = subscriptionRepository.findByOrganizerId(organizer.getId())
                .map(sub -> sub.getPlan().getName())
                .orElse("FREE");
        boolean isVip = !"FREE".equalsIgnoreCase(planName) || quota.getVipPurchased() > 0;
        return new OrganizerQuotaSummaryDto(
                planName,
                quota.getFreeGranted(),
                quota.getFreeConsumed(),
                quota.getVipPurchased(),
                quota.getVipConsumed(),
                quota.getAvailableRaffles(),
                isVip,
                quota.getAvailableRaffles() > 0
        );
    }

    /** Retorna true si el organizer puede crear al menos una rifa más. */
    @Transactional(readOnly = true)
    public boolean hasAvailableQuota(UUID organizerId) {
        return quotaRepository.findByOrganizerId(organizerId)
                .map(q -> q.getAvailableRaffles() > 0)
                .orElse(false);
    }

    /**
     * Valida cupo antes de crear la rifa. Inicializa cuota si no existe (organizers legados).
     * Se llama ANTES de persistir la rifa; consumeRaffleQuota hace el decremento con lock.
     */
    @Transactional
    public void assertCanCreate(OrganizerProfile organizer) {
        OrganizerQuota quota = getOrCreateQuota(organizer);
        if (quota.getAvailableRaffles() <= 0) {
            throw new PlanLimitExceededException(
                    "No tienes cupo disponible para crear rifas. " +
                    "Adquiere un paquete VIP para obtener más cupos.");
        }
    }

    /**
     * Consume 1 cupo al crear una rifa.
     * Usa bloqueo pesimista para evitar race conditions concurrentes.
     * Se llama DENTRO de la misma transacción que persiste la rifa.
     */
    @Transactional
    public void consumeRaffleQuota(UUID organizerId, UUID raffleId) {
        OrganizerQuota quota = quotaRepository.findByOrganizerIdForUpdate(organizerId)
                .orElseThrow(() -> new BusinessException("Cuota no inicializada para el organizer"));

        if (quota.getAvailableRaffles() <= 0) {
            throw new BusinessException("Sin cupo disponible. No se puede crear la rifa.");
        }

        // Consumir desde FREE primero; luego desde VIP
        if (quota.getFreeConsumed() < quota.getFreeGranted()) {
            quota.setFreeConsumed(quota.getFreeConsumed() + 1);
        } else {
            quota.setVipConsumed(quota.getVipConsumed() + 1);
        }
        quota.setAvailableRaffles(quota.getAvailableRaffles() - 1);
        quotaRepository.save(quota);
    }

    /**
     * Acredita cupos VIP al organizer (por canje de código o compra aprobada).
     * Siempre acumula sobre el saldo existente.
     * Las fases 5 y 6 (códigos y MP) llamarán a este método.
     */
    @Transactional
    public void creditVipQuota(UUID organizerId, int quantity, String reference) {
        OrganizerQuota quota = quotaRepository.findByOrganizerIdForUpdate(organizerId)
                .orElseThrow(() -> new BusinessException("Cuota no inicializada para el organizer"));

        quota.setVipPurchased(quota.getVipPurchased() + quantity);
        quota.setAvailableRaffles(quota.getAvailableRaffles() + quantity);
        quotaRepository.save(quota);
    }

    /**
     * Política de devolución de cupo al eliminar una rifa.
     * Solo devuelve cupo si la rifa estaba en DRAFT (nunca publicada).
     * Las fases posteriores conectarán esto a RaffleService.delete().
     */
    @Transactional
    public void restoreQuotaIfApplicable(OrganizerProfile organizer, Raffle raffle) {
        if (raffle.getPublicationStatus() != PublicationStatus.DRAFT) {
            return; // Rifas publicadas/activas no devuelven cupo
        }

        OrganizerQuota quota = quotaRepository.findByOrganizerIdForUpdate(organizer.getId())
                .orElse(null);
        if (quota == null) return;

        // Restaurar preferentemente desde VIP, luego FREE (inverso al consumo)
        if (quota.getVipConsumed() > 0) {
            quota.setVipConsumed(quota.getVipConsumed() - 1);
        } else if (quota.getFreeConsumed() > 0) {
            quota.setFreeConsumed(quota.getFreeConsumed() - 1);
        }
        quota.setAvailableRaffles(quota.getAvailableRaffles() + 1);
        quotaRepository.save(quota);
    }
}
