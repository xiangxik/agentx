package com.agentx.backend.knowledge.domain;

import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface KnowledgeChunkRepository extends JpaRepository<KnowledgeChunk, Long> {
  void deleteByKnowledgeSourceId(Long knowledgeSourceId);

  List<KnowledgeChunk> findByKnowledgeSourceIdOrderByChunkIndexAsc(Long knowledgeSourceId);

  List<KnowledgeChunk> findByTenantIdAndChatbotIdAndKnowledgeSourceIdInOrderByKnowledgeSourceIdAscChunkIndexAsc(
      Long tenantId, Long chatbotId, Collection<Long> knowledgeSourceIds);
}