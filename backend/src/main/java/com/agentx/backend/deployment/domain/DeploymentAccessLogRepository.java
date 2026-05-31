package com.agentx.backend.deployment.domain;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DeploymentAccessLogRepository extends JpaRepository<DeploymentAccessLog, Long> {

  List<DeploymentAccessLog> findTop10ByChatbotIdOrderByCreatedAtDescIdDesc(Long chatbotId);
}