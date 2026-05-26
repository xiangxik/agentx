package com.agentx.backend.model.domain;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ModelProviderRepository extends JpaRepository<ModelProvider, Long> {
  List<ModelProvider> findByStatusOrderByIdAsc(ModelProviderStatus status);

  Optional<ModelProvider> findByProviderCode(String providerCode);
}