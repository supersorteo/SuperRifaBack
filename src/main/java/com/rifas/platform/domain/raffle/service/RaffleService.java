package com.rifas.platform.domain.raffle.service;

import com.rifas.platform.domain.organizer.entity.OrganizerProfile;
import com.rifas.platform.domain.organizer.repository.OrganizerProfileRepository;
import com.rifas.platform.domain.payment.entity.PaymentMethod;
import com.rifas.platform.domain.payment.repository.PaymentMethodRepository;
import com.rifas.platform.domain.raffle.dto.CreateRaffleRequest;
import com.rifas.platform.domain.raffle.dto.RafflePublicResponse;
import com.rifas.platform.domain.raffle.entity.*;
import com.rifas.platform.domain.raffle.repository.RaffleNumberRepository;
import com.rifas.platform.domain.raffle.repository.RaffleRepository;
import com.rifas.platform.shared.audit.service.AuditService;
import com.rifas.platform.domain.user.entity.User;
import com.rifas.platform.shared.enums.*;
import com.rifas.platform.shared.exception.BusinessException;
import com.rifas.platform.shared.exception.ResourceNotFoundException;
import com.rifas.platform.shared.security.UserDetailsImpl;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.text.Normalizer;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;

@Service
@RequiredArgsConstructor
@Transactional
public class RaffleService {

    private final RaffleRepository raffleRepository;
    private final RaffleNumberRepository raffleNumberRepository;
    private final OrganizerProfileRepository organizerProfileRepository;
    private final PaymentMethodRepository paymentMethodRepository;
    private final AuditService auditService;

    public Raffle create(CreateRaffleRequest req) {
        OrganizerProfile organizer = currentOrganizer();

        String slug = generateUniqueSlug(req.title());

        Raffle raffle = Raffle.builder()
                .title(req.title())
                .slug(slug)
                .description(req.description())
                .organizer(organizer)
                .totalNumbers(req.totalNumbers())
                .rangeStart(req.rangeStart() != null ? req.rangeStart() : 1)
                .rangeEnd(req.rangeEnd() != null ? req.rangeEnd() : req.totalNumbers())
                .pricePerNumber(req.pricePerNumber())
                .drawDateTime(req.drawDateTime())
                .timezone(req.timezone() != null ? req.timezone() : "America/Argentina/Buenos_Aires")
                .drawMethod(req.drawMethod() != null ? req.drawMethod() : DrawMethod.MANUAL)
                .drawPolicy(req.drawPolicy() != null ? req.drawPolicy() : DrawPolicy.PAID_ONLY)
                .termsAndConditions(req.termsAndConditions())
                .build();

        if (req.prizeName() != null) {
            RafflePrize prize = RafflePrize.builder()
                    .raffle(raffle)
                    .name(req.prizeName())
                    .description(req.prizeDescription())
                    .estimatedValue(req.prizeEstimatedValue())
                    .build();
            raffle.setPrize(prize);
        }

        Raffle saved = raffleRepository.save(raffle);

        // Crear todos los RaffleNumber en batch
        List<RaffleNumber> numbers = IntStream
                .rangeClosed(saved.getRangeStart(), saved.getRangeEnd())
                .mapToObj(n -> RaffleNumber.builder()
                        .raffle(saved)
                        .number(n)
                        .status(NumberStatus.AVAILABLE)
                        .build())
                .toList();
        raffleNumberRepository.saveAll(numbers);

        auditService.log("RAFFLE_CREATED", "Raffle", saved.getId(), null, saved.getTitle());
        return saved;
    }

    public Raffle publish(UUID raffleId) {
        Raffle raffle = findOwnedRaffle(raffleId);
        if (raffle.getPublicationStatus() != PublicationStatus.DRAFT) {
            throw new BusinessException("Solo se pueden publicar rifas en borrador");
        }
        raffle.setPublicationStatus(PublicationStatus.PUBLISHED);
        auditService.log("RAFFLE_PUBLISHED", "Raffle", raffleId, null, null);
        return raffleRepository.save(raffle);
    }

    public Raffle pause(UUID raffleId) {
        Raffle raffle = findOwnedRaffle(raffleId);
        raffle.setPublicationStatus(PublicationStatus.PAUSED);
        return raffleRepository.save(raffle);
    }

    @Transactional(readOnly = true)
    public RafflePublicResponse getPublicRaffle(String slug) {
        Raffle raffle = raffleRepository.findBySlug(slug)
                .orElseThrow(() -> new ResourceNotFoundException("Rifa no encontrada"));

        if (raffle.getPublicationStatus() != PublicationStatus.PUBLISHED) {
            throw new ResourceNotFoundException("Rifa no disponible");
        }

        List<PaymentMethod> methods = paymentMethodRepository
                .findByOrganizerIdAndActiveAndPublicVisibleTrueOrderByDisplayOrderAsc(
                        raffle.getOrganizer().getId(), true);

        long available = raffleNumberRepository.countByRaffleAndStatus(raffle, NumberStatus.AVAILABLE);
        long reserved  = raffleNumberRepository.countByRaffleAndStatus(raffle, NumberStatus.RESERVED)
                + raffleNumberRepository.countByRaffleAndStatus(raffle, NumberStatus.PENDING_PAYMENT);
        long paid      = raffleNumberRepository.countByRaffleAndStatus(raffle, NumberStatus.PAID);

        OrganizerProfile org = raffle.getOrganizer();
        User organizerUser = org.getUser();

        return new RafflePublicResponse(
                raffle.getId(), raffle.getTitle(), raffle.getSlug(), raffle.getDescription(),
                raffle.getPrize() != null ? new RafflePublicResponse.PrizeInfo(
                        raffle.getPrize().getName(), raffle.getPrize().getDescription(),
                        raffle.getPrize().getEstimatedValue(), raffle.getPrize().getImageUrl()) : null,
                raffle.getImages().stream().map(i -> new RafflePublicResponse.ImageInfo(
                        i.getUrl(), i.getAltText(), i.isCoverImage(), i.getDisplayOrder())).toList(),
                raffle.getPricePerNumber(),
                raffle.getTotalNumbers(),
                (int) available, (int) reserved, (int) paid,
                raffle.getDrawDateTime(), raffle.getTimezone(),
                raffle.getOperationalStatus(), raffle.getPublicationStatus(),
                new RafflePublicResponse.OrganizerPublicInfo(
                        org.getBusinessName() != null ? org.getBusinessName() : organizerUser.getFullName(),
                        org.getAvatarUrl(), org.getWhatsappNumber()),
                raffle.getWinnerNumber(), null, raffle.getExecutedAt(),
                methods.stream().map(m -> new RafflePublicResponse.PaymentMethodPublicInfo(
                        m.getType().name(), m.getDisplayName(), m.getAlias(),
                        m.getCbu() != null ? m.getCbu() : m.getCvu(),
                        m.getAccountHolder(), m.getInstructions())).toList()
        );
    }

    @Transactional(readOnly = true)
    public List<RafflePublicResponse.NumberInfo> getNumbers(String slug) {
        Raffle raffle = raffleRepository.findBySlug(slug)
                .orElseThrow(() -> new ResourceNotFoundException("Rifa no encontrada"));
        return raffleNumberRepository.findByRaffleOrderByNumberAsc(raffle).stream()
                .map(n -> new RafflePublicResponse.NumberInfo(n.getNumber(), n.getStatus()))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<Raffle> getMyRaffles() {
        return raffleRepository.findByOrganizerIdOrderByCreatedAtDesc(currentOrganizer().getId());
    }

    private Raffle findOwnedRaffle(UUID raffleId) {
        Raffle raffle = raffleRepository.findById(raffleId)
                .orElseThrow(() -> new ResourceNotFoundException("Rifa no encontrada"));
        if (!raffle.getOrganizer().getId().equals(currentOrganizer().getId())) {
            throw new BusinessException("No tiene permisos sobre esta rifa");
        }
        return raffle;
    }

    private OrganizerProfile currentOrganizer() {
        UserDetailsImpl ud = (UserDetailsImpl) SecurityContextHolder
                .getContext().getAuthentication().getPrincipal();
        return organizerProfileRepository.findByUserId(ud.getId())
                .orElseThrow(() -> new BusinessException("Perfil de organizador no encontrado"));
    }

    private String generateUniqueSlug(String title) {
        String base = Normalizer.normalize(title, Normalizer.Form.NFD)
                .replaceAll("[\\p{InCombiningDiacriticalMarks}]", "")
                .toLowerCase().replaceAll("[^a-z0-9]+", "-")
                .replaceAll("^-|-$", "");

        String slug = base;
        int attempt = 0;
        while (raffleRepository.existsBySlug(slug)) {
            slug = base + "-" + (++attempt);
        }
        return slug;
    }

}
