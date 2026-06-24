package com.rifas.platform.domain.vip.service;

import com.rifas.platform.domain.vip.dto.VipPackageDto;
import com.rifas.platform.domain.vip.dto.VipPackageRequest;
import com.rifas.platform.domain.vip.entity.VipPackage;
import com.rifas.platform.domain.vip.repository.VipPackageRepository;
import com.rifas.platform.shared.exception.BusinessException;
import com.rifas.platform.shared.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class VipPackageService {

    private final VipPackageRepository packageRepository;

    @Transactional(readOnly = true)
    public List<VipPackageDto> findAllActive() {
        return packageRepository.findAllByActiveTrueOrderByDisplayOrderAsc()
                .stream().map(this::toDto).toList();
    }

    @Transactional(readOnly = true)
    public List<VipPackageDto> findAll() {
        return packageRepository.findAll().stream()
                .sorted((a, b) -> Integer.compare(a.getDisplayOrder(), b.getDisplayOrder()))
                .map(this::toDto).toList();
    }

    @Transactional
    public VipPackageDto create(VipPackageRequest req) {
        if (packageRepository.existsByName(req.name())) {
            throw new BusinessException("Ya existe un paquete con ese nombre");
        }
        VipPackage pkg = VipPackage.builder()
                .name(req.name())
                .raffleQuantity(req.raffleQuantity())
                .price(req.price())
                .currency(req.currency() != null ? req.currency() : "USD")
                .active(true)
                .displayOrder(req.displayOrder() != null ? req.displayOrder() : 0)
                .build();
        return toDto(packageRepository.save(pkg));
    }

    @Transactional
    public VipPackageDto update(UUID id, VipPackageRequest req) {
        VipPackage pkg = packageRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Paquete VIP no encontrado"));
        if (!pkg.getName().equals(req.name()) && packageRepository.existsByName(req.name())) {
            throw new BusinessException("Ya existe un paquete con ese nombre");
        }
        pkg.setName(req.name());
        pkg.setRaffleQuantity(req.raffleQuantity());
        pkg.setPrice(req.price());
        if (req.currency() != null) pkg.setCurrency(req.currency());
        if (req.displayOrder() != null) pkg.setDisplayOrder(req.displayOrder());
        return toDto(packageRepository.save(pkg));
    }

    @Transactional
    public VipPackageDto toggleActive(UUID id) {
        VipPackage pkg = packageRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Paquete VIP no encontrado"));
        pkg.setActive(!pkg.isActive());
        return toDto(packageRepository.save(pkg));
    }

    private VipPackageDto toDto(VipPackage pkg) {
        return new VipPackageDto(pkg.getId(), pkg.getName(), pkg.getRaffleQuantity(),
                pkg.getPrice(), pkg.getCurrency(), pkg.getDisplayOrder(), pkg.isActive());
    }
}
