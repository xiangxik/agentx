package com.agentx.backend.chatbot.domain;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ChatbotAppearanceRepository extends JpaRepository<ChatbotAppearance, Long> {
  Optional<ChatbotAppearance> findByChatbotId(Long chatbotId);
}
