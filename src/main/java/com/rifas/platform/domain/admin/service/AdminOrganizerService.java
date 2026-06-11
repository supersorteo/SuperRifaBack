package com.rifas.platform.domain.admin.service;

import com.rifas.platform.domain.admin.dto.AdminOrganizerDetailDto;
import com.rifas.platform.domain.admin.dto.AdminOrganizerDto;
import com.rifas.platform.domain.admin.dto.AdminRaffleDto;
import com.rifas.platform.domain.execution.repository.RaffleExecutionRepository;
import com.rifas.platform.domain.organizer.entity.OrganizerProfile;
import com.rifas.platform.domain.organizer.repository.OrganizerProfileRepository;
import com.rifas.platform.domain.payment.repository.PaymentMethodRepository;
import com.rifas.platform.domain.plan.repository.SubscriptionRepository;
import com.rifas.platform.domain.raffle.entity.Raffle;
import com.rifas.platform.domain.raffle.entity.RaffleImage;
import com.rifas.platform.domain.raffle.repository.RaffleImageRepository;
import com.rifas.platform.domain.raffle.repository.RaffleNumberRepository;
import com.rifas.platform.domain.raffle.repository.RaffleRepository;
import com.rifas.platform.domain.raffle.service.ImageStorageService;
import com.rifas.platform.domain.reservation.entity.Reservation;
import com.rifas.platform.domain.reservation.repository.ReservationRepository;
import com.rifas.platform.domain.user.repository.UserRepository;
import com.rifas.platform.shared.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AdminOrganizerService {

    private final OrganizerProfileRepository organizerProfileRepository;
    private final UserRepository             userRepository;
    private final RaffleRepository           raffleRepository;
    private final RaffleImageRepository      raffleImageRepository;
    private final RaffleNumberRepository     raffleNumberRepository;
    private final RaffleExecutionRepository  raffleExecutionRepository;
    private final ReservationRepository      reservationRepository;
    private final PaymentMethodRepository    paymentMethodRepository;
    private final SubscriptionRepository     subscriptionRepository;
    private final ImageStorageService        imageStorageService;

    @Transactional(readOnly = true)
    public List<AdminOrganizerDto> getAllOrganizers() {
        return organizerProfileRepository.findAllWithUser().stream()
                .map(p -> AdminOrganizerDto.from(p,
                        raffleRepository.countByOrganizerId(p.getId())))
                .toList();
    }

    @Transactional(readOnly = true)
    public AdminOrganizerDetailDto getDetail(UUID organizerId) {
        OrganizerProfile p = organizerProfileRepository.findAllWithUser().stream()
                .filter(o -> o.getId().equals(organizerId))
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("Organizador no encontrado"));

        List<AdminRaffleDto> raffles = raffleRepository
                .findByOrganizerIdOrderByCreatedAtDesc(organizerId).stream()
                .map(r -> AdminRaffleDto.from(r,
                        reservationRepository.countDistinctParticipantsByRaffleId(r.getId())))
                .toList();

        return AdminOrganizerDetailDto.from(p, raffles);
    }

    @Transactional
    public void deleteRaffle(UUID raffleId) {
        Raffle raffle = raffleRepository.findById(raffleId)
                .orElseThrow(() -> new ResourceNotFoundException("Rifa no encontrada"));
        deleteRaffleInternal(raffle);
    }

    @Transactional
    public void deleteOrganizer(UUID organizerId) {
        OrganizerProfile organizer = organizerProfileRepository.findById(organizerId)
                .orElseThrow(() -> new ResourceNotFoundException("Organizador no encontrado"));

        raffleRepository.findByOrganizerIdOrderByCreatedAtDesc(organizerId)
                .forEach(this::deleteRaffleInternal);

        paymentMethodRepository.deleteByOrganizerId(organizerId);
        subscriptionRepository.deleteByOrganizerId(organizerId);
        organizerProfileRepository.delete(organizer);
        userRepository.delete(organizer.getUser());
    }

    private void deleteRaffleInternal(Raffle raffle) {
        UUID raffleId = raffle.getId();

        raffleImageRepository.findByRaffleIdOrderByDisplayOrder(raffleId).stream()
                .map(RaffleImage::getPublicId)
                .filter(id -> id != null && !id.isBlank())
                .forEach(imageStorageService::delete);

        raffleExecutionRepository.findByRaffle(raffle)
                .ifPresent(raffleExecutionRepository::delete);

        raffleNumberRepository.deleteByRaffle(raffle);

        List<Reservation> reservations = reservationRepository.findByRaffleId(raffleId);
        if (!reservations.isEmpty()) {
            reservationRepository.deleteAll(reservations);
        }

        raffleRepository.delete(raffle);
    }
}
