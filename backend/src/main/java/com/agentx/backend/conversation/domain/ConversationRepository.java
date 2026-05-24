package com.agentx.backend.conversation.domain;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ConversationRepository extends JpaRepository<Conversation, Long> {
  Optional<Conversation> findByIdAndTenantId(Long id, Long tenantId);
}
