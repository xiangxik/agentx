package com.agentx.backend.knowledge.application;

import com.agentx.backend.model.application.ModelEmbeddingService;
import com.agentx.backend.knowledge.domain.KnowledgeChunk;
import com.agentx.backend.knowledge.domain.KnowledgeChunkRepository;
import com.agentx.backend.knowledge.domain.KnowledgeSource;
import com.agentx.backend.knowledge.domain.KnowledgeSourceRepository;
import com.agentx.backend.knowledge.domain.KnowledgeSourceStatus;
import com.agentx.backend.knowledge.domain.KnowledgeSourceType;
import javax.sql.DataSource;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@Service
public class KnowledgeRetrievalService {

  private final KnowledgeChunkRepository knowledgeChunkRepository;
  private final KnowledgeSourceRepository knowledgeSourceRepository;
  private final ModelEmbeddingService modelEmbeddingService;
  private final ObjectMapper objectMapper;
  private final KnowledgeChunkVectorStore knowledgeChunkVectorStore;
  private final JdbcTemplate jdbcTemplate;

  public KnowledgeRetrievalService(
      KnowledgeChunkRepository knowledgeChunkRepository,
      KnowledgeSourceRepository knowledgeSourceRepository,
      ModelEmbeddingService modelEmbeddingService,
      ObjectMapper objectMapper,
      KnowledgeChunkVectorStore knowledgeChunkVectorStore,
      DataSource dataSource) {
    this.knowledgeChunkRepository = knowledgeChunkRepository;
    this.knowledgeSourceRepository = knowledgeSourceRepository;
    this.modelEmbeddingService = modelEmbeddingService;
    this.objectMapper = objectMapper;
    this.knowledgeChunkVectorStore = knowledgeChunkVectorStore;
    this.jdbcTemplate = new JdbcTemplate(dataSource);
  }

  @Transactional
  public RetrievalResult search(Long tenantId, Long chatbotId, Long conversationId, String query) {
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
    ModelEmbeddingService.QueryEmbeddingResult queryEmbeddingResult =
      modelEmbeddingService.embedQuery(tenantId, chatbotId, conversationId, query);
    double[] queryEmbedding = parseEmbedding(queryEmbeddingResult.embeddingJson());

    RetrievalResult databaseResult =
      searchByPgVector(
          tenantId,
          chatbotId,
          queryEmbeddingResult.embeddingJson(),
          queryEmbeddingResult.dimensions(),
          normalizedQuery);
    if (databaseResult != null) {
      return databaseResult;
    }

    return knowledgeChunkRepository
        .findByTenantIdAndChatbotIdAndKnowledgeSourceIdInOrderByKnowledgeSourceIdAscChunkIndexAsc(
            tenantId, chatbotId, sourcesById.keySet())
        .stream()
      .map(
        chunk ->
          scoreChunk(
            chunk,
            normalizedQuery,
            queryEmbedding,
            sourcesById.get(chunk.getKnowledgeSourceId())))
      .filter(scored -> scored.score() >= 30)
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

  private RetrievalResult searchByPgVector(
      Long tenantId,
      Long chatbotId,
      String queryEmbeddingJson,
      int queryEmbeddingDimension,
      String normalizedQuery) {
    if (!knowledgeChunkVectorStore.isPgvectorReady()
        || queryEmbeddingJson == null
        || queryEmbeddingJson.isBlank()
        || queryEmbeddingDimension <= 0) {
      return null;
    }

    List<RetrievalResult> results;
    try {
      results =
          jdbcTemplate.query(
              """
              select
                kc.knowledge_source_id,
                ks.source_name,
                kc.content,
                case when ks.source_type = 'WEB' then ks.source_uri else null end as source_link,
                cast(round((1 - (kc.embedding_vector <=> cast(? as vector))) * 1000) as integer) as score
              from knowledge_chunk kc
              join knowledge_source ks on ks.id = kc.knowledge_source_id
              where kc.tenant_id = ?
                and kc.chatbot_id = ?
                and ks.status = 'ACTIVE'
                and kc.embedding_dimension = ?
                and kc.embedding_vector is not null
              order by kc.embedding_vector <=> cast(? as vector) asc
              limit 1
              """,
              (resultSet, rowNum) ->
                  new RetrievalResult(
                      true,
                      resultSet.getLong("knowledge_source_id"),
                      resultSet.getString("source_name"),
                      resultSet.getString("content"),
                      resultSet.getString("source_link"),
                      resultSet.getInt("score")),
              queryEmbeddingJson,
              tenantId,
              chatbotId,
              queryEmbeddingDimension,
              queryEmbeddingJson);
    } catch (RuntimeException exception) {
      return null;
    }

    if (results.isEmpty()) {
      return null;
    }

    RetrievalResult result = results.get(0);
    if (result.score() < 450) {
      return null;
    }
    if (normalize(result.content()).contains(normalizedQuery)) {
      return new RetrievalResult(
          true, result.sourceId(), result.sourceName(), result.content(), result.sourceLink(), Math.max(result.score(), 700));
    }
    return result;
  }

  private ScoredChunk scoreChunk(
      KnowledgeChunk chunk, String normalizedQuery, double[] queryEmbedding, KnowledgeSource source) {
    String normalizedContent = normalize(chunk.getContent());
    if (normalizedContent.isBlank()) {
      return new ScoredChunk(chunk, source, 0);
    }

    int lexicalScore = lexicalScore(normalizedContent, normalizedQuery);
    int embeddingScore = embeddingScore(chunk.getEmbeddingJson(), queryEmbedding);
    return new ScoredChunk(chunk, source, Math.max(lexicalScore, embeddingScore));
  }

  private int lexicalScore(String normalizedContent, String normalizedQuery) {
    if (normalizedContent.contains(normalizedQuery)) {
      return 700 + normalizedQuery.length() * 20;
    }

    Set<String> queryTerms = tokenize(normalizedQuery);
    Set<String> contentTerms = tokenize(normalizedContent);
    int overlap = (int) queryTerms.stream().filter(contentTerms::contains).count();
    int bigramOverlap = (int) characterBigrams(normalizedQuery).stream().filter(characterBigrams(normalizedContent)::contains).count();
    return Math.max(overlap * 120, bigramOverlap * 45);
  }

  private int embeddingScore(String embeddingJson, double[] queryEmbedding) {
    if (embeddingJson == null || embeddingJson.isBlank() || queryEmbedding.length == 0) {
      return 0;
    }
    double[] chunkEmbedding = parseEmbedding(embeddingJson);
    if (chunkEmbedding.length == 0 || chunkEmbedding.length != queryEmbedding.length) {
      return 0;
    }
    double similarity = cosineSimilarity(chunkEmbedding, queryEmbedding);
    if (similarity < 0.45D) {
      return 0;
    }
    return (int) Math.round(similarity * 1000D);
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

  private double[] parseEmbedding(String embeddingJson) {
    if (embeddingJson == null || embeddingJson.isBlank()) {
      return new double[0];
    }
    try {
      java.util.List<?> rawValues = objectMapper.readValue(embeddingJson, java.util.List.class);
      double[] values = new double[rawValues.size()];
      for (int index = 0; index < rawValues.size(); index++) {
        Object rawValue = rawValues.get(index);
        if (rawValue instanceof Number number) {
          values[index] = number.doubleValue();
        } else {
          values[index] = Double.parseDouble(String.valueOf(rawValue));
        }
      }
      return values;
    } catch (JacksonException exception) {
      return new double[0];
    }
  }

  private double cosineSimilarity(double[] left, double[] right) {
    double dot = 0.0;
    double leftNorm = 0.0;
    double rightNorm = 0.0;
    for (int index = 0; index < left.length; index++) {
      dot += left[index] * right[index];
      leftNorm += left[index] * left[index];
      rightNorm += right[index] * right[index];
    }
    if (leftNorm == 0.0 || rightNorm == 0.0) {
      return 0.0;
    }
    return dot / (Math.sqrt(leftNorm) * Math.sqrt(rightNorm));
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