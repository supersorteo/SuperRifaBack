package com.rifas.platform.domain.reservation.controller;

import com.rifas.platform.domain.raffle.entity.RaffleNumber;
import com.rifas.platform.domain.raffle.repository.RaffleNumberRepository;
import com.rifas.platform.domain.reservation.dto.CreateReservationRequest;
import com.rifas.platform.domain.reservation.dto.ParticipantLookupDto;
import com.rifas.platform.domain.reservation.dto.ReservationCreatedDto;
import com.rifas.platform.domain.reservation.entity.Reservation;
import com.rifas.platform.domain.reservation.repository.ReservationRepository;
import com.rifas.platform.domain.reservation.service.ReservationService;
import com.rifas.platform.shared.exception.ResourceNotFoundException;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/public/reservations")
@RequiredArgsConstructor
public class ReservationController {

    private final ReservationService reservationService;
    private final ReservationRepository reservationRepository;
    private final RaffleNumberRepository raffleNumberRepository;

    @PostMapping
    public ResponseEntity<ReservationCreatedDto> create(@Valid @RequestBody CreateReservationRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(reservationService.createReservation(req));
    }

    @GetMapping("/lookup")
    public ResponseEntity<ParticipantLookupDto> lookup(
            @RequestParam String phone,
            @RequestParam String slug) {

        List<Reservation> reservations = reservationRepository
                .findByParticipantPhoneAndRaffleSlug(phone.trim(), slug.trim());

        if (reservations.isEmpty()) {
            throw new ResourceNotFoundException("No se encontraron reservas para ese teléfono en esta rifa");
        }

        Reservation first = reservations.get(0);
        List<ParticipantLookupDto.ReservationSummary> summaries = reservations.stream()
                .map(r -> {
                    List<Integer> numbers = raffleNumberRepository.findByReservationId(r.getId())
                            .stream().map(RaffleNumber::getNumber).toList();
                    return new ParticipantLookupDto.ReservationSummary(
                            r.getId(), numbers, r.getTotalAmount(),
                            r.getStatus(), r.getCreatedAt(), r.getExpiresAt());
                })
                .toList();

        return ResponseEntity.ok(new ParticipantLookupDto(
                first.getParticipant().getFullName(),
                first.getRaffle().getTitle(),
                first.getRaffle().getSlug(),
                summaries
        ));
    }
}
