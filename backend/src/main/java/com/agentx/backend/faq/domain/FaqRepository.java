package com.agentx.backend.faq.domain;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FaqRepository extends JpaRepository<Faq, Long> {
  List<Faq> findByTenantIdAndChatbotId(Long tenantId, Long chatbotId);

  List<Faq> findByTenantIdAndChatbotIdAndStatus(Long tenantId, Long chatbotId, FaqStatus status);
}
