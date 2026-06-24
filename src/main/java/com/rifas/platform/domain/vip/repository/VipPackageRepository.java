package com.rifas.platform.domain.vip.repository;

import com.rifas.platform.domain.vip.entity.VipPackage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface VipPackageRepository extends JpaRepository<VipPackage, UUID> {

    List<VipPackage> findAllByActiveTrueOrderByDisplayOrderAsc();

    Optional<VipPackage> findByName(String name);

    boolean existsByName(String name);
}
