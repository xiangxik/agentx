package com.agentx.backend.knowledge.domain;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface KnowledgeSourceRepository extends JpaRepository<KnowledgeSource, Long> {
    Optional<KnowledgeSource> findByIdAndTenantIdAndChatbotIdAndStatusNot(
            Long id, Long tenantId, Long chatbotId, KnowledgeSourceStatus status);

      List<KnowledgeSource> findByTenantIdAndChatbotIdAndStatus(
          Long tenantId, Long chatbotId, KnowledgeSourceStatus status);

  List<KnowledgeSource> findByTenantIdAndChatbotIdAndStatusNotOrderByIdDesc(
      Long tenantId, Long chatbotId, KnowledgeSourceStatus status);

  long countByTenantIdAndSourceTypeAndStatusNot(
      Long tenantId, KnowledgeSourceType sourceType, KnowledgeSourceStatus status);

  List<KnowledgeSource> findByTenantIdAndSourceTypeAndStatusNot(
      Long tenantId, KnowledgeSourceType sourceType, KnowledgeSourceStatus status);
}