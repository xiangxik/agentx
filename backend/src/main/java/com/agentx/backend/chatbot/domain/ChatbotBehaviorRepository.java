package com.agentx.backend.chatbot.domain;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ChatbotBehaviorRepository extends JpaRepository<ChatbotBehavior, Long> {
  Optional<ChatbotBehavior> findByChatbotId(Long chatbotId);
}
