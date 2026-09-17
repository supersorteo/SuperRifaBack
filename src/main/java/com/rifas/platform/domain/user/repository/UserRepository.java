package com.rifas.platform.domain.user.repository;

import com.rifas.platform.domain.user.entity.User;
import com.rifas.platform.shared.enums.RoleName;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserRepository extends JpaRepository<User, UUID> {
    Optional<User> findByEmailIgnoreCase(String email);
    boolean existsByEmailIgnoreCase(String email);
    Optional<User> findByFullNameIgnoreCase(String fullName);

    @Query("SELECT u FROM User u JOIN u.roles r WHERE LOWER(u.fullName) = LOWER(:name) AND r.name = :role")
    Optional<User> findAdminByUsername(@Param("name") String name, @Param("role") RoleName role);
}
