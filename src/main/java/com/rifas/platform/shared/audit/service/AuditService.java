package com.rifas.platform.shared.audit.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rifas.platform.shared.audit.entity.AuditLog;
import com.rifas.platform.shared.audit.repository.AuditLogRepository;
import com.rifas.platform.shared.security.UserDetailsImpl;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuditService {

    private final AuditLogRepository auditLogRepository;
    private final ObjectMapper objectMapper;

    public void log(String action, String entityType, UUID entityId, Object previous, Object current) {
        try {
            UUID userId = null;
            String userEmail = "system";

            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth != null && auth.getPrincipal() instanceof UserDetailsImpl ud) {
                userId = ud.getId();
                userEmail = ud.getEmail();
            }

            AuditLog entry = AuditLog.builder()
                    .userId(userId)
                    .userEmail(userEmail)
                    .action(action)
                    .entityType(entityType)
                    .entityId(entityId)
                    .previousState(previous != null ? objectMapper.writeValueAsString(previous) : null)
                    .newState(current != null ? objectMapper.writeValueAsString(current) : null)
                    .build();

            auditLogRepository.save(entry);
        } catch (Exception e) {
            log.warn("No se pudo guardar audit log para {}/{}: {}", entityType, entityId, e.getMessage());
        }
    }
}
