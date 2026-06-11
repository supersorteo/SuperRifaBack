package com.rifas.platform.domain.user.service;

import com.rifas.platform.domain.user.dto.AdminLoginRequest;
import com.rifas.platform.domain.organizer.entity.OrganizerProfile;
import org.springframework.security.authentication.BadCredentialsException;
import com.rifas.platform.domain.organizer.repository.OrganizerProfileRepository;
import com.rifas.platform.domain.plan.entity.Plan;
import com.rifas.platform.domain.plan.entity.Subscription;
import com.rifas.platform.domain.plan.repository.PlanRepository;
import com.rifas.platform.domain.plan.repository.SubscriptionRepository;
import com.rifas.platform.domain.user.dto.LoginRequest;
import com.rifas.platform.domain.user.dto.RegisterRequest;
import com.rifas.platform.domain.user.dto.TokenResponse;
import com.rifas.platform.domain.user.entity.Role;
import com.rifas.platform.domain.user.entity.User;
import com.rifas.platform.domain.user.repository.RoleRepository;
import com.rifas.platform.domain.user.repository.UserRepository;
import com.rifas.platform.shared.enums.RoleName;
import com.rifas.platform.shared.enums.SubscriptionStatus;
import com.rifas.platform.shared.exception.BusinessException;
import com.rifas.platform.shared.security.JwtProperties;
import com.rifas.platform.shared.security.JwtTokenProvider;
import com.rifas.platform.shared.security.UserDetailsImpl;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final OrganizerProfileRepository organizerProfileRepository;
    private final PlanRepository planRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final JwtTokenProvider jwtTokenProvider;
    private final JwtProperties jwtProperties;

    @Transactional
    public TokenResponse register(RegisterRequest req) {
        if (userRepository.existsByEmail(req.email())) {
            throw new BusinessException("Ya existe una cuenta con ese email");
        }

        Role organizerRole = roleRepository.findByName(RoleName.ROLE_ORGANIZER)
                .orElseGet(() -> roleRepository.save(Role.builder().name(RoleName.ROLE_ORGANIZER).build()));

        User user = User.builder()
                .email(req.email())
                .passwordHash(passwordEncoder.encode(req.password()))
                .fullName(req.fullName())
                .roles(Set.of(organizerRole))
                .build();

        userRepository.save(user);

        OrganizerProfile profile = OrganizerProfile.builder()
                .user(user)
                .phone(req.phone())
                .build();
        organizerProfileRepository.save(profile);

        Plan freePlan = planRepository.findAllByActiveTrueOrderByDisplayOrderAsc()
                .stream().findFirst()
                .orElse(null);

        if (freePlan != null) {
            subscriptionRepository.save(Subscription.builder()
                    .organizer(profile)
                    .plan(freePlan)
                    .status(SubscriptionStatus.ACTIVE)
                    .startDate(LocalDateTime.now())
                    .build());
        }

        Authentication auth = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(req.email(), req.password()));

        return buildTokenResponse(auth);
    }

    public TokenResponse login(LoginRequest req) {
        Authentication auth = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(req.email(), req.password()));
        return buildTokenResponse(auth);
    }

    public TokenResponse adminLogin(AdminLoginRequest req) {
        User user = userRepository.findByFullNameIgnoreCase(req.username())
                .orElseThrow(() -> new BadCredentialsException("Credenciales incorrectas"));

        boolean isAdmin = user.getRoles().stream()
                .anyMatch(r -> r.getName() == RoleName.ROLE_ADMIN);
        if (!isAdmin) throw new BadCredentialsException("Credenciales incorrectas");

        if (!passwordEncoder.matches(req.password(), user.getPasswordHash()))
            throw new BadCredentialsException("Credenciales incorrectas");

        UserDetailsImpl ud = UserDetailsImpl.build(user);
        Authentication auth = new UsernamePasswordAuthenticationToken(ud, null, ud.getAuthorities());
        return buildTokenResponse(auth);
    }

    private TokenResponse buildTokenResponse(Authentication auth) {
        UserDetailsImpl ud = (UserDetailsImpl) auth.getPrincipal();
        String access  = jwtTokenProvider.generateAccessToken(auth);
        String refresh = jwtTokenProvider.generateRefreshToken(ud.getEmail());
        Set<String> roles = ud.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority).collect(Collectors.toSet());
        return TokenResponse.of(access, refresh,
                jwtProperties.getExpirationMs() / 1000L,
                ud.getEmail(), ud.getFullName(), roles);
    }
}
