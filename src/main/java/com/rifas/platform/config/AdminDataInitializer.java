package com.rifas.platform.config;

import com.rifas.platform.domain.user.entity.Role;
import com.rifas.platform.domain.user.entity.User;
import com.rifas.platform.domain.user.repository.RoleRepository;
import com.rifas.platform.domain.user.repository.UserRepository;
import com.rifas.platform.shared.enums.RoleName;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;

@Component
@RequiredArgsConstructor
@Slf4j
public class AdminDataInitializer implements ApplicationRunner {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final AdminSeedProperties seedProps;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        Role adminRole = roleRepository.findByName(RoleName.ROLE_ADMIN)
                .orElseGet(() -> roleRepository.save(
                        Role.builder().name(RoleName.ROLE_ADMIN).build()));

        seedAdmin(adminRole, seedProps.user1());
        seedAdmin(adminRole, seedProps.user2());
    }

    private void seedAdmin(Role role, AdminSeedProperties.AdminUser u) {
        if (userRepository.existsByEmail(u.email())) return;
        User user = User.builder()
                .email(u.email())
                .passwordHash(passwordEncoder.encode(u.password()))
                .fullName(u.name())
                .roles(Set.of(role))
                .active(true)
                .emailVerified(true)
                .build();
        userRepository.save(user);
        log.info("Admin creado: {}", u.email());
    }
}
