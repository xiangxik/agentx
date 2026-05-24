package com.agentx.backend.faq.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "faq")
public class Faq {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "tenant_id", nullable = false)
  private Long tenantId;

  @Column(name = "chatbot_id", nullable = false)
  private Long chatbotId;

  @Column(nullable = false)
  private String language;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private FaqStatus status = FaqStatus.ACTIVE;

  @Column(nullable = false)
  private String question;

  @Column(name = "alternate_questions")
  private String alternateQuestions;

  @Column(nullable = false)
  private String answer;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt = Instant.now();

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt = Instant.now();

  public Long getId() {
    return id;
  }

  public Long getTenantId() {
    return tenantId;
  }

  public void setTenantId(Long tenantId) {
    this.tenantId = tenantId;
  }

  public Long getChatbotId() {
    return chatbotId;
  }

  public void setChatbotId(Long chatbotId) {
    this.chatbotId = chatbotId;
  }

  public String getLanguage() {
    return language;
  }

  public void setLanguage(String language) {
    this.language = language;
  }

  public FaqStatus getStatus() {
    return status;
  }

  public void setStatus(FaqStatus status) {
    this.status = status;
  }

  public String getQuestion() {
    return question;
  }

  public void setQuestion(String question) {
    this.question = question;
  }

  public String getAlternateQuestions() {
    return alternateQuestions;
  }

  public void setAlternateQuestions(String alternateQuestions) {
    this.alternateQuestions = alternateQuestions;
  }

  public String getAnswer() {
    return answer;
  }

  public void setAnswer(String answer) {
    this.answer = answer;
  }
}
