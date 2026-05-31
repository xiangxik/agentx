package com.agentx.backend.deployment.domain;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DeploymentWhitelistDomainRepository
    extends JpaRepository<DeploymentWhitelistDomain, Long> {

  List<DeploymentWhitelistDomain> findByChatbotIdOrderByDomainAsc(Long chatbotId);

  Optional<DeploymentWhitelistDomain> findByChatbotIdAndDomain(Long chatbotId, String domain);

  long countByChatbotId(Long chatbotId);
}