package com.rifas.platform.domain.participant.repository;

import com.rifas.platform.domain.participant.entity.Participant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface ParticipantRepository extends JpaRepository<Participant, UUID> {
    Optional<Participant> findByEmailAndPhone(String email, String phone);
    Optional<Participant> findByPhone(String phone);
}
