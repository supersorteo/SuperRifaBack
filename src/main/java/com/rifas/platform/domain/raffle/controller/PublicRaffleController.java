package com.rifas.platform.domain.raffle.controller;

import com.rifas.platform.domain.raffle.dto.RafflePublicResponse;
import com.rifas.platform.domain.raffle.service.RaffleService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/public/raffles")
@RequiredArgsConstructor
public class PublicRaffleController {

    private final RaffleService raffleService;

    @GetMapping("/{slug}")
    public ResponseEntity<RafflePublicResponse> getBySlug(@PathVariable String slug) {
        return ResponseEntity.ok(raffleService.getPublicRaffle(slug));
    }

    @GetMapping("/{slug}/numbers")
    public ResponseEntity<List<RafflePublicResponse.NumberInfo>> getNumbers(@PathVariable String slug) {
        return ResponseEntity.ok(raffleService.getNumbers(slug));
    }
}
