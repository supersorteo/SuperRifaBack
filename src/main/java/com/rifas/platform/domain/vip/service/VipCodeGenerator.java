package com.rifas.platform.domain.vip.service;

import com.rifas.platform.domain.vip.repository.VipCodeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.security.SecureRandom;

@Component
@RequiredArgsConstructor
public class VipCodeGenerator {

    private static final String CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";

    private final VipCodeRepository codeRepository;
    private final SecureRandom random = new SecureRandom();

    public String generateUniqueCode(int raffleQuantity) {
        String code;
        do {
            code = buildCode(raffleQuantity);
        } while (codeRepository.existsByCode(code));
        return code;
    }

    private String buildCode(int raffleQuantity) {
        String prefix = "VIP" + raffleQuantity;
        int firstBlockLength = switch (raffleQuantity) {
            case 3 -> 3;
            case 5 -> 4;
            case 10 -> 5;
            default -> raffleQuantity <= 3 ? 3 : raffleQuantity <= 5 ? 4 : 5;
        };
        int secondBlockLength = 3;
        return prefix + "-" + randomBlock(firstBlockLength) + "-" + randomBlock(secondBlockLength);
    }

    private String randomBlock(int length) {
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            sb.append(CHARS.charAt(random.nextInt(CHARS.length())));
        }
        return sb.toString();
    }
}
