package com.agentx.backend.model.domain;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ModelDefinitionRepository extends JpaRepository<ModelDefinition, Long> {
  List<ModelDefinition> findByProviderIdOrderByIdAsc(Long providerId);

  List<ModelDefinition> findByStatusAndPurposeOrderByIsDefaultDescIdAsc(
      ModelDefinitionStatus status, ModelPurpose purpose);

  List<ModelDefinition> findByPurposeOrderByIdAsc(ModelPurpose purpose);

  Optional<ModelDefinition> findByIdAndProviderId(Long id, Long providerId);
}