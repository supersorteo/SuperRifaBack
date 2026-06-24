package com.rifas.platform.config;

import com.rifas.platform.domain.plan.entity.Plan;
import com.rifas.platform.domain.plan.repository.PlanRepository;
import com.rifas.platform.domain.vip.entity.VipPackage;
import com.rifas.platform.domain.vip.repository.VipPackageRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

@Component
@RequiredArgsConstructor
@Slf4j
@Order(2)
public class VipDataInitializer implements ApplicationRunner {

    private final PlanRepository planRepository;
    private final VipPackageRepository vipPackageRepository;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        seedFreePlan();
        seedVipPackages();
    }

    private void seedFreePlan() {
        if (!planRepository.findAllByActiveTrueOrderByDisplayOrderAsc().isEmpty()) return;

        planRepository.save(Plan.builder()
                .name("FREE")
                .maxActiveRaffles(1)
                .maxRafflesPerMonth(1)
                .maxNumbersPerRaffle(1000)
                .active(true)
                .displayOrder(0)
                .build());
        log.info("Plan FREE creado");
    }

    private void seedVipPackages() {
        if (!vipPackageRepository.findAllByActiveTrueOrderByDisplayOrderAsc().isEmpty()) return;

        vipPackageRepository.save(VipPackage.builder()
                .name("3 Rifas VIP")
                .raffleQuantity(3)
                .price(new BigDecimal("10.00"))
                .currency("USD")
                .active(true)
                .displayOrder(1)
                .build());

        vipPackageRepository.save(VipPackage.builder()
                .name("5 Rifas VIP")
                .raffleQuantity(5)
                .price(new BigDecimal("15.00"))
                .currency("USD")
                .active(true)
                .displayOrder(2)
                .build());

        vipPackageRepository.save(VipPackage.builder()
                .name("10 Rifas VIP")
                .raffleQuantity(10)
                .price(new BigDecimal("25.00"))
                .currency("USD")
                .active(true)
                .displayOrder(3)
                .build());

        log.info("Paquetes VIP creados: 3, 5, 10 rifas");
    }
}
