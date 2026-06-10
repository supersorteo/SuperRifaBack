package com.rifas.platform.domain.user.repository;

import com.rifas.platform.domain.user.entity.Role;
import com.rifas.platform.shared.enums.RoleName;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface RoleRepository extends JpaRepository<Role, Integer> {
    Optional<Role> findByName(RoleName name);
}
