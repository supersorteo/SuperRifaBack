package com.rifas.platform.domain.reservation.controller;

import com.rifas.platform.domain.reservation.dto.OrganizerReservationDto;
import com.rifas.platform.domain.reservation.service.OrganizerReservationService;
import com.rifas.platform.shared.enums.ReservationStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/organizer/reservations")
@RequiredArgsConstructor
public class OrganizerReservationController {

    private final OrganizerReservationService reservationService;

    @GetMapping
    public ResponseEntity<Page<OrganizerReservationDto>> list(
            @RequestParam(required = false) UUID raffleId,
            @RequestParam(required = false) ReservationStatus status,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        return ResponseEntity.ok(reservationService.listReservations(raffleId, status, pageable));
    }

    @PutMapping("/{id}/confirm")
    public ResponseEntity<OrganizerReservationDto> confirm(@PathVariable UUID id) {
        return ResponseEntity.ok(reservationService.confirmReservation(id));
    }

    @PutMapping("/{id}/cancel")
    public ResponseEntity<OrganizerReservationDto> cancel(@PathVariable UUID id) {
        return ResponseEntity.ok(reservationService.cancelReservation(id));
    }
}
