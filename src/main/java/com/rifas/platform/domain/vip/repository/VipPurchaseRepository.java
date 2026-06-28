package com.rifas.platform.domain.vip.repository;

import com.rifas.platform.domain.vip.entity.VipPurchase;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface VipPurchaseRepository extends JpaRepository<VipPurchase, UUID> {

    @Query("""
            SELECT p FROM VipPurchase p
            JOIN FETCH p.vipPackage
            WHERE p.organizer.id = :organizerId
            ORDER BY p.createdAt DESC
            """)
    List<VipPurchase> findByOrganizerWithPackage(@Param("organizerId") UUID organizerId);

    @Query("""
            SELECT p FROM VipPurchase p
            JOIN FETCH p.organizer o
            JOIN FETCH o.user
            JOIN FETCH p.vipPackage
            ORDER BY p.createdAt DESC
            """)
    List<VipPurchase> findAllWithDetails();

    List<VipPurchase> findAllByOrganizerId(UUID organizerId);

    boolean existsByVipPackageId(UUID vipPackageId);

    void deleteByOrganizerId(UUID organizerId);

    /** Idempotencia: un payment_id de MP nunca debe procesarse dos veces. */
    Optional<VipPurchase> findByExternalPaymentId(String externalPaymentId);

    boolean existsByExternalPaymentId(String externalPaymentId);
}
