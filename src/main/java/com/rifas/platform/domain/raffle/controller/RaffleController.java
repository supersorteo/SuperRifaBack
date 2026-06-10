package com.rifas.platform.domain.raffle.controller;

import com.rifas.platform.domain.raffle.dto.CreateRaffleRequest;
import com.rifas.platform.domain.raffle.dto.OrganizerRaffleResponse;
import com.rifas.platform.domain.raffle.service.RaffleService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/organizer/raffles")
@RequiredArgsConstructor
public class RaffleController {

    private final RaffleService raffleService;

    @PostMapping
    public ResponseEntity<OrganizerRaffleResponse> create(@Valid @RequestBody CreateRaffleRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(raffleService.create(req));
    }

    @GetMapping
    public ResponseEntity<List<OrganizerRaffleResponse>> myRaffles() {
        return ResponseEntity.ok(raffleService.getMyRaffles());
    }

    @PostMapping("/{id}/publish")
    public ResponseEntity<OrganizerRaffleResponse> publish(@PathVariable UUID id) {
        return ResponseEntity.ok(raffleService.publish(id));
    }

    @PostMapping("/{id}/pause")
    public ResponseEntity<OrganizerRaffleResponse> pause(@PathVariable UUID id) {
        return ResponseEntity.ok(raffleService.pause(id));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        raffleService.delete(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/images")
    public ResponseEntity<Void> uploadImages(
            @PathVariable UUID id,
            @RequestParam("files") List<MultipartFile> files) throws IOException {
        raffleService.uploadImages(id, files);
        return ResponseEntity.noContent().build();
    }
}
