package com.agentx.backend.auth.domain;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AppUserRepository extends JpaRepository<AppUser, Long> {
  Optional<AppUser> findByEmail(String email);

  Optional<AppUser> findFirstByTenantId(Long tenantId);
}
