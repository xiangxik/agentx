package com.agentx.backend.knowledge.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "knowledge_chunk")
public class KnowledgeChunk {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "tenant_id", nullable = false)
  private Long tenantId;

  @Column(name = "chatbot_id", nullable = false)
  private Long chatbotId;

  @Column(name = "knowledge_source_id", nullable = false)
  private Long knowledgeSourceId;

  @Column(name = "chunk_index", nullable = false)
  private Integer chunkIndex;

  @Column(nullable = false)
  private String content;

  @Column
  private String summary;

  @Column(name = "source_link")
  private String sourceLink;

  @Column(name = "embedding_ref")
  private String embeddingRef;

  @Column(name = "embedding_json", columnDefinition = "TEXT")
  private String embeddingJson;

  @Column(name = "embedding_dimension")
  private Integer embeddingDimension;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt = Instant.now();

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

  public Long getKnowledgeSourceId() {
    return knowledgeSourceId;
  }

  public void setKnowledgeSourceId(Long knowledgeSourceId) {
    this.knowledgeSourceId = knowledgeSourceId;
  }

  public Integer getChunkIndex() {
    return chunkIndex;
  }

  public void setChunkIndex(Integer chunkIndex) {
    this.chunkIndex = chunkIndex;
  }

  public String getContent() {
    return content;
  }

  public void setContent(String content) {
    this.content = content;
  }

  public String getSummary() {
    return summary;
  }

  public void setSummary(String summary) {
    this.summary = summary;
  }

  public String getSourceLink() {
    return sourceLink;
  }

  public void setSourceLink(String sourceLink) {
    this.sourceLink = sourceLink;
  }

  public String getEmbeddingRef() {
    return embeddingRef;
  }

  public void setEmbeddingRef(String embeddingRef) {
    this.embeddingRef = embeddingRef;
  }

  public String getEmbeddingJson() {
    return embeddingJson;
  }

  public void setEmbeddingJson(String embeddingJson) {
    this.embeddingJson = embeddingJson;
  }

  public Integer getEmbeddingDimension() {
    return embeddingDimension;
  }

  public void setEmbeddingDimension(Integer embeddingDimension) {
    this.embeddingDimension = embeddingDimension;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }
}