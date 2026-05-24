package com.agentx.backend.chatbot.domain;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ChatbotRepository extends JpaRepository<Chatbot, Long> {
  List<Chatbot> findByTenantId(Long tenantId);

  Optional<Chatbot> findByIdAndTenantId(Long id, Long tenantId);

  Optional<Chatbot> findByPublicCode(String publicCode);
}
