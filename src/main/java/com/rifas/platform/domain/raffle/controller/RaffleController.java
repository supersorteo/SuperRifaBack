package com.rifas.platform.domain.raffle.controller;

import com.rifas.platform.domain.raffle.dto.CreateRaffleRequest;
import com.rifas.platform.domain.raffle.entity.Raffle;
import com.rifas.platform.domain.raffle.service.RaffleService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/organizer/raffles")
@RequiredArgsConstructor
public class RaffleController {

    private final RaffleService raffleService;

    @PostMapping
    public ResponseEntity<Raffle> create(@Valid @RequestBody CreateRaffleRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(raffleService.create(req));
    }

    @GetMapping
    public ResponseEntity<List<Raffle>> myRaffles() {
        return ResponseEntity.ok(raffleService.getMyRaffles());
    }

    @PostMapping("/{id}/publish")
    public ResponseEntity<Raffle> publish(@PathVariable UUID id) {
        return ResponseEntity.ok(raffleService.publish(id));
    }

    @PostMapping("/{id}/pause")
    public ResponseEntity<Raffle> pause(@PathVariable UUID id) {
        return ResponseEntity.ok(raffleService.pause(id));
    }
}
