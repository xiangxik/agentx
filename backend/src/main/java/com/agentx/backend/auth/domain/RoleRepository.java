package com.agentx.backend.auth.domain;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RoleRepository extends JpaRepository<Role, Long> {
  Optional<Role> findByCode(String code);
}
