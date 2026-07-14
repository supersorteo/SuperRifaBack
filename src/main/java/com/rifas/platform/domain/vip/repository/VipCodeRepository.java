package com.rifas.platform.domain.vip.repository;

import com.rifas.platform.domain.vip.entity.VipCode;
import com.rifas.platform.shared.enums.VipCodeStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface VipCodeRepository extends JpaRepository<VipCode, UUID> {

    Optional<VipCode> findByCode(String code);

    boolean existsByCode(String code);

    List<VipCode> findAllByStatusOrderByCreatedAtDesc(VipCodeStatus status);

    List<VipCode> findAllByAssignedOrganizerIdOrderByCreatedAtDesc(UUID organizerId);

    List<VipCode> findAllByRedeemedByOrganizerIdOrderByRedeemedAtDesc(UUID organizerId);

    int countByVipPackageIdAndStatus(UUID vipPackageId, VipCodeStatus status);

    boolean existsByVipPackageId(UUID vipPackageId);
}
