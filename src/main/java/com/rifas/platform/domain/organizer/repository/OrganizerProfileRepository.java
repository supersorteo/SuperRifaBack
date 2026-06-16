package com.rifas.platform.domain.organizer.repository;

import com.rifas.platform.domain.organizer.entity.OrganizerProfile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface OrganizerProfileRepository extends JpaRepository<OrganizerProfile, UUID> {
    Optional<OrganizerProfile> findByUserId(UUID userId);
    boolean existsByUserId(UUID userId);

    @Query("SELECT p FROM OrganizerProfile p JOIN FETCH p.user WHERE p.user.id = :userId")
    Optional<OrganizerProfile> findByUserIdWithUser(UUID userId);

    @Query("SELECT p FROM OrganizerProfile p JOIN FETCH p.user ORDER BY p.createdAt DESC")
    List<OrganizerProfile> findAllWithUser();
}
