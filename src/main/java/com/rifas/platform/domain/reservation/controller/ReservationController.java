package com.rifas.platform.domain.reservation.controller;

import com.rifas.platform.domain.raffle.entity.RaffleNumber;
import com.rifas.platform.domain.raffle.repository.RaffleNumberRepository;
import com.rifas.platform.domain.reservation.dto.CreateReservationRequest;
import com.rifas.platform.domain.reservation.dto.ParticipantLookupDto;
import com.rifas.platform.domain.reservation.dto.ReservationCreatedDto;
import com.rifas.platform.domain.reservation.entity.Reservation;
import com.rifas.platform.domain.reservation.repository.ReservationRepository;
import com.rifas.platform.domain.payment.service.MercadoPagoService;
import com.rifas.platform.domain.reservation.service.ReservationService;
import com.rifas.platform.shared.exception.ResourceNotFoundException;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/public/reservations")
@RequiredArgsConstructor
public class ReservationController {

    private final ReservationService reservationService;
    private final ReservationRepository reservationRepository;
    private final RaffleNumberRepository raffleNumberRepository;
    private final MercadoPagoService mercadoPagoService;

    public record PreferenceResponse(String preferenceId, String checkoutUrl) {}

    @PostMapping
    public ResponseEntity<ReservationCreatedDto> create(@Valid @RequestBody CreateReservationRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(reservationService.createReservation(req));
    }

    @PostMapping("/{id}/preference")
    public ResponseEntity<PreferenceResponse> createPreference(@PathVariable UUID id) {
        Reservation reservation = reservationRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Reserva no encontrada"));
        MercadoPagoService.PreferenceResult result = mercadoPagoService.createPreference(reservation);
        return ResponseEntity.ok(new PreferenceResponse(result.preferenceId(), result.checkoutUrl()));
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
