package com.agentx.backend.knowledge.application;

import com.agentx.backend.knowledge.domain.KnowledgeChunk;
import com.agentx.backend.knowledge.domain.KnowledgeChunkRepository;
import com.agentx.backend.knowledge.domain.KnowledgeSource;
import com.agentx.backend.knowledge.domain.KnowledgeSourceRepository;
import com.agentx.backend.knowledge.domain.KnowledgeSourceStatus;
import com.agentx.backend.knowledge.domain.KnowledgeSourceType;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class KnowledgeRetrievalService {

  private final KnowledgeChunkRepository knowledgeChunkRepository;
  private final KnowledgeSourceRepository knowledgeSourceRepository;

  public KnowledgeRetrievalService(
      KnowledgeChunkRepository knowledgeChunkRepository,
      KnowledgeSourceRepository knowledgeSourceRepository) {
    this.knowledgeChunkRepository = knowledgeChunkRepository;
    this.knowledgeSourceRepository = knowledgeSourceRepository;
  }

  @Transactional(readOnly = true)
  public RetrievalResult search(Long tenantId, Long chatbotId, String query) {
    String normalizedQuery = normalize(query);
    if (normalizedQuery.isBlank()) {
      return new RetrievalResult(false, null, null, null, null, 0);
    }

    List<KnowledgeSource> activeSources =
        knowledgeSourceRepository.findByTenantIdAndChatbotIdAndStatus(
            tenantId, chatbotId, KnowledgeSourceStatus.ACTIVE);
    if (activeSources.isEmpty()) {
      return new RetrievalResult(false, null, null, null, null, 0);
    }

    Map<Long, KnowledgeSource> sourcesById =
        activeSources.stream().collect(Collectors.toMap(KnowledgeSource::getId, Function.identity()));

    return knowledgeChunkRepository
        .findByTenantIdAndChatbotIdAndKnowledgeSourceIdInOrderByKnowledgeSourceIdAscChunkIndexAsc(
            tenantId, chatbotId, sourcesById.keySet())
        .stream()
        .map(chunk -> scoreChunk(chunk, normalizedQuery, sourcesById.get(chunk.getKnowledgeSourceId())))
        .filter(scored -> scored.score() > 0)
        .max(Comparator.comparingInt(ScoredChunk::score))
        .map(
            scored ->
                new RetrievalResult(
                    true,
                    scored.chunk().getKnowledgeSourceId(),
                    scored.source().getSourceName(),
                    scored.chunk().getContent(),
                  scored.source().getSourceType() == KnowledgeSourceType.WEB
                    ? scored.source().getSourceUri()
                    : null,
                    scored.score()))
        .orElseGet(() -> new RetrievalResult(false, null, null, null, null, 0));
  }

  private ScoredChunk scoreChunk(
      KnowledgeChunk chunk, String normalizedQuery, KnowledgeSource source) {
    String normalizedContent = normalize(chunk.getContent());
    if (normalizedContent.isBlank()) {
      return new ScoredChunk(chunk, source, 0);
    }

    if (normalizedContent.contains(normalizedQuery)) {
      return new ScoredChunk(chunk, source, 100 + normalizedQuery.length());
    }

    Set<String> queryTerms = tokenize(normalizedQuery);
    Set<String> contentTerms = tokenize(normalizedContent);
    int overlap = (int) queryTerms.stream().filter(contentTerms::contains).count();
    int bigramOverlap = (int) characterBigrams(normalizedQuery).stream().filter(characterBigrams(normalizedContent)::contains).count();
    return new ScoredChunk(chunk, source, Math.max(overlap, bigramOverlap));
  }

  private Set<String> tokenize(String value) {
    return Arrays.stream(value.split("[^\\p{L}\\p{N}]+"))
        .map(String::trim)
        .filter(token -> !token.isBlank())
        .collect(Collectors.toSet());
  }

  private Set<String> characterBigrams(String value) {
    String compact = value.replaceAll("[^\\p{L}\\p{N}]", "");
    if (compact.length() < 2) {
      return compact.isBlank() ? Set.of() : Set.of(compact);
    }
    Set<String> grams = new HashSet<>();
    for (int index = 0; index < compact.length() - 1; index++) {
      grams.add(compact.substring(index, index + 2));
    }
    return grams;
  }

  private String normalize(String value) {
    return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
  }

  private record ScoredChunk(KnowledgeChunk chunk, KnowledgeSource source, int score) {}

  public record RetrievalResult(
      boolean matched,
      Long sourceId,
      String sourceName,
      String content,
      String sourceLink,
      int score) {}
}