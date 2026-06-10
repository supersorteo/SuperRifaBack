package com.rifas.platform.domain.raffle.service;

import com.rifas.platform.domain.organizer.entity.OrganizerProfile;
import com.rifas.platform.domain.organizer.repository.OrganizerProfileRepository;
import com.rifas.platform.domain.execution.entity.RaffleExecution;
import com.rifas.platform.domain.execution.repository.RaffleExecutionRepository;
import com.rifas.platform.domain.payment.entity.PaymentMethod;
import com.rifas.platform.domain.payment.repository.PaymentMethodRepository;
import com.rifas.platform.domain.raffle.dto.CreateRaffleRequest;
import com.rifas.platform.domain.raffle.dto.OrganizerRaffleResponse;
import com.rifas.platform.domain.raffle.dto.RafflePublicResponse;
import com.rifas.platform.domain.raffle.entity.*;
import com.rifas.platform.domain.reservation.entity.Reservation;
import com.rifas.platform.domain.reservation.repository.ReservationRepository;
import com.rifas.platform.domain.raffle.repository.RaffleImageRepository;
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

import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.text.Normalizer;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;

@Service
@RequiredArgsConstructor
@Transactional
public class RaffleService {

    private final RaffleRepository raffleRepository;
    private final RaffleNumberRepository raffleNumberRepository;
    private final RaffleImageRepository raffleImageRepository;
    private final ReservationRepository reservationRepository;
    private final RaffleExecutionRepository raffleExecutionRepository;
    private final OrganizerProfileRepository organizerProfileRepository;
    private final PaymentMethodRepository paymentMethodRepository;
    private final AuditService auditService;
    private final ImageStorageService imageStorageService;

    public OrganizerRaffleResponse create(CreateRaffleRequest req) {
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
        return toOrganizerResponse(saved);
    }

    public OrganizerRaffleResponse publish(UUID raffleId) {
        Raffle raffle = findOwnedRaffle(raffleId);
        if (raffle.getPublicationStatus() != PublicationStatus.DRAFT) {
            throw new BusinessException("Solo se pueden publicar rifas en borrador");
        }
        raffle.setPublicationStatus(PublicationStatus.PUBLISHED);
        auditService.log("RAFFLE_PUBLISHED", "Raffle", raffleId, null, null);
        return toOrganizerResponse(raffleRepository.save(raffle));
    }

    public OrganizerRaffleResponse pause(UUID raffleId) {
        Raffle raffle = findOwnedRaffle(raffleId);
        raffle.setPublicationStatus(PublicationStatus.PAUSED);
        return toOrganizerResponse(raffleRepository.save(raffle));
    }

    public void delete(UUID raffleId) {
        Raffle raffle = findOwnedRaffle(raffleId);

        raffleImageRepository.findByRaffleIdOrderByDisplayOrder(raffleId).stream()
                .map(RaffleImage::getPublicId)
                .filter(publicId -> publicId != null && !publicId.isBlank())
                .forEach(imageStorageService::delete);

        raffleExecutionRepository.findByRaffle(raffle)
                .ifPresent(raffleExecutionRepository::delete);

        raffleNumberRepository.deleteByRaffle(raffle);

        List<Reservation> reservations = reservationRepository.findByRaffleId(raffleId);
        if (!reservations.isEmpty()) {
            reservationRepository.deleteAll(reservations);
        }

        auditService.log("RAFFLE_DELETED", "Raffle", raffleId, null, raffle.getTitle());
        raffleRepository.delete(raffle);
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
    public List<OrganizerRaffleResponse> getMyRaffles() {
        return raffleRepository.findByOrganizerIdOrderByCreatedAtDesc(currentOrganizer().getId()).stream()
                .map(this::toOrganizerResponse)
                .toList();
    }

    public void uploadImages(UUID raffleId, List<MultipartFile> files) throws IOException {
        Raffle raffle = findOwnedRaffle(raffleId);
        long existing = raffleImageRepository.countByRaffleId(raffleId);
        if (existing + files.size() > 5) {
            throw new BusinessException("La rifa ya tiene el máximo de 5 imágenes");
        }
        int order = (int) existing;
        for (MultipartFile file : files) {
            ImageStorageService.UploadResult result = imageStorageService.upload(file, raffleId);
            RaffleImage img = RaffleImage.builder()
                    .raffle(raffle)
                    .url(result.url())
                    .publicId(result.publicId())
                    .displayOrder(order++)
                    .coverImage(order == 1)
                    .build();
            raffleImageRepository.save(img);
        }
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

    private OrganizerRaffleResponse toOrganizerResponse(Raffle raffle) {
        long participantCount = reservationRepository.countDistinctParticipantsByRaffleId(raffle.getId());

        return new OrganizerRaffleResponse(
                raffle.getId(),
                raffle.getTitle(),
                raffle.getSlug(),
                raffle.getPrize() != null ? raffle.getPrize().getName() : null,
                participantCount,
                raffle.getPublicationStatus(),
                raffle.getOperationalStatus(),
                raffle.getTotalNumbers(),
                raffle.getPricePerNumber(),
                raffle.getDrawDateTime(),
                raffle.getCreatedAt(),
                raffle.getImages().stream()
                        .map(image -> new OrganizerRaffleResponse.ImageInfo(
                                image.getUrl(),
                                image.getAltText(),
                                image.isCoverImage(),
                                image.getDisplayOrder()))
                        .toList()
        );
    }

}
