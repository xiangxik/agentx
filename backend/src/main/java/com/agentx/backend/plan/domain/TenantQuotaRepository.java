package com.agentx.backend.plan.domain;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TenantQuotaRepository extends JpaRepository<TenantQuota, Long> {
  Optional<TenantQuota> findByTenantId(Long tenantId);
}
