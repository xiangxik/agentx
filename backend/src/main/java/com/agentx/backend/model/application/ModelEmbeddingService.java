package com.agentx.backend.model.application;

import com.agentx.backend.chatbot.domain.ChatbotBehavior;
import com.agentx.backend.chatbot.domain.ChatbotBehaviorRepository;
import com.agentx.backend.model.domain.ModelCallLog;
import com.agentx.backend.model.domain.ModelCallLogRepository;
import com.agentx.backend.model.domain.ModelCallStatus;
import com.agentx.backend.model.domain.ModelDefinition;
import com.agentx.backend.model.domain.ModelDefinitionRepository;
import com.agentx.backend.model.domain.ModelDefinitionStatus;
import com.agentx.backend.model.domain.ModelProvider;
import com.agentx.backend.model.domain.ModelProviderRepository;
import com.agentx.backend.model.domain.ModelPurpose;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.ArrayList;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Service
public class ModelEmbeddingService {

  private static final String BUILTIN_PROVIDER_CODE = "AGENTX_BUILTIN";
  private static final String BUILTIN_EMBEDDING_MODEL = "agentx-knowledge-embedding-v1";
  private static final int BUILTIN_VECTOR_SIZE = 64;
  private static final HttpClient HTTP_CLIENT =
      HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

  private final ModelProviderRepository modelProviderRepository;
  private final ModelDefinitionRepository modelDefinitionRepository;
  private final ModelCallLogRepository modelCallLogRepository;
  private final ChatbotBehaviorRepository chatbotBehaviorRepository;
  private final ObjectMapper objectMapper;

  public ModelEmbeddingService(
      ModelProviderRepository modelProviderRepository,
      ModelDefinitionRepository modelDefinitionRepository,
      ModelCallLogRepository modelCallLogRepository,
      ChatbotBehaviorRepository chatbotBehaviorRepository,
      ObjectMapper objectMapper) {
    this.modelProviderRepository = modelProviderRepository;
    this.modelDefinitionRepository = modelDefinitionRepository;
    this.modelCallLogRepository = modelCallLogRepository;
    this.chatbotBehaviorRepository = chatbotBehaviorRepository;
    this.objectMapper = objectMapper;
  }

  @Transactional
  public EmbeddingResult generateEmbeddingReference(
      Long tenantId,
      Long chatbotId,
      Long knowledgeSourceId,
      int chunkIndex,
      String content,
      String sourceLink) {
    ActiveEmbeddingSelection selection = resolveSelection(chatbotId);
    int promptTokens = estimateTokens(content);
    EmbeddingVector vector = embed(content, selection);
    String embeddingRef =
        selection.providerCode()
            + ":"
            + selection.modelCode()
            + ":"
            + hashReference(
                tenantId + ":" + chatbotId + ":" + knowledgeSourceId + ":" + chunkIndex + ":" + content);

    Map<String, Object> metadata = new LinkedHashMap<>();
    metadata.put("mode", "KNOWLEDGE_INDEXING");
    metadata.put("knowledgeSourceId", knowledgeSourceId);
    metadata.put("chunkIndex", chunkIndex);
    metadata.put("transport", selection.transport());
    metadata.put("providerDisplayName", selection.providerDisplayName());
    metadata.put("embeddingRef", embeddingRef);
    metadata.put("dimensions", vector.values().size());
    if (sourceLink != null && !sourceLink.isBlank()) {
      metadata.put("sourceLink", sourceLink);
    }

    ModelCallLog log = new ModelCallLog();
    log.setTenantId(tenantId);
    log.setChatbotId(chatbotId);
    log.setConversationId(null);
    log.setAssistantMessageId(null);
    log.setProviderCode(selection.providerCode());
    log.setModelCode(selection.modelCode());
    log.setPurpose(ModelPurpose.EMBEDDING);
    log.setStatus(ModelCallStatus.SUCCESS);
    log.setPromptTokens(promptTokens);
    log.setCompletionTokens(0);
    log.setTotalTokens(promptTokens);
    log.setEstimatedCost((promptTokens / 1000D) * selection.inputPricePer1k());
    log.setRetryCount(0);
    log.setLatencyMs(0);
    log.setErrorMessage(null);
    log.setMetadataJson(toJson(metadata));
    log.setCreatedAt(Instant.now());
    ModelCallLog savedLog = modelCallLogRepository.save(log);

    return new EmbeddingResult(
        embeddingRef,
        vector.serialized(),
      vector.values().size(),
        selection.providerCode(),
        selection.modelCode(),
        savedLog.getId());
  }

  @Transactional
  public QueryEmbeddingResult embedQuery(Long tenantId, Long chatbotId, Long conversationId, String query) {
    ActiveEmbeddingSelection selection = resolveSelection(chatbotId);
    int promptTokens = estimateTokens(query);
    EmbeddingVector vector = embed(query, selection);
    Map<String, Object> metadata = new LinkedHashMap<>();
    metadata.put("mode", "KNOWLEDGE_QUERY");
    metadata.put("transport", selection.transport());
    metadata.put("providerDisplayName", selection.providerDisplayName());
    metadata.put("dimensions", vector.values().size());

    ModelCallLog log = new ModelCallLog();
    log.setTenantId(tenantId);
    log.setChatbotId(chatbotId);
    log.setConversationId(conversationId);
    log.setAssistantMessageId(null);
    log.setProviderCode(selection.providerCode());
    log.setModelCode(selection.modelCode());
    log.setPurpose(ModelPurpose.EMBEDDING);
    log.setStatus(ModelCallStatus.SUCCESS);
    log.setPromptTokens(promptTokens);
    log.setCompletionTokens(0);
    log.setTotalTokens(promptTokens);
    log.setEstimatedCost((promptTokens / 1000D) * selection.inputPricePer1k());
    log.setRetryCount(0);
    log.setLatencyMs(0);
    log.setErrorMessage(null);
    log.setMetadataJson(toJson(metadata));
    log.setCreatedAt(Instant.now());
    ModelCallLog savedLog = modelCallLogRepository.save(log);

    return new QueryEmbeddingResult(
        vector.serialized(),
        vector.values().size(),
        selection.providerCode(),
        selection.modelCode(),
        savedLog.getId());
  }

  private ActiveEmbeddingSelection resolveSelection(Long chatbotId) {
    List<ModelDefinition> activeEmbeddingModels =
        modelDefinitionRepository.findByStatusAndPurposeOrderByIsDefaultDescIdAsc(
            ModelDefinitionStatus.ACTIVE, ModelPurpose.EMBEDDING);
    if (activeEmbeddingModels.isEmpty()) {
      return new ActiveEmbeddingSelection(
          BUILTIN_PROVIDER_CODE,
          BUILTIN_EMBEDDING_MODEL,
          BUILTIN_PROVIDER_CODE,
          0.0,
          "BUILTIN",
          null,
          null,
          null);
    }

    ActiveEmbeddingSelection chatbotSelection =
        resolveChatbotSelection(chatbotId, activeEmbeddingModels);
    if (chatbotSelection != null) {
      return chatbotSelection;
    }

    ModelDefinition definition = activeEmbeddingModels.get(0);
    return toSelection(definition, modelProviderRepository.findById(definition.getProviderId()).orElseThrow());
  }

  private ActiveEmbeddingSelection resolveChatbotSelection(
      Long chatbotId, List<ModelDefinition> activeEmbeddingModels) {
    if (chatbotId == null) {
      return null;
    }
    ChatbotBehavior behavior = chatbotBehaviorRepository.findByChatbotId(chatbotId).orElse(null);
    if (behavior == null) {
      return null;
    }
    Map<String, Object> config = fromJson(behavior.getConfigJson());
    String providerCode = emptyToNull(String.valueOf(config.getOrDefault("embeddingProviderCode", "")));
    String modelCode = emptyToNull(String.valueOf(config.getOrDefault("embeddingModelCode", "")));
    if (providerCode == null || modelCode == null) {
      return null;
    }

    for (ModelDefinition definition : activeEmbeddingModels) {
      if (!modelCode.equals(definition.getModelCode())) {
        continue;
      }
      ModelProvider provider = modelProviderRepository.findById(definition.getProviderId()).orElseThrow();
      if (providerCode.equals(provider.getProviderCode())) {
        return toSelection(definition, provider);
      }
    }
    throw new IllegalStateException("CHATBOT_EMBEDDING_MODEL_NOT_FOUND");
  }

  private ActiveEmbeddingSelection toSelection(ModelDefinition definition, ModelProvider provider) {
    Map<String, Object> metadata = fromJson(provider.getMetadataJson());
    return new ActiveEmbeddingSelection(
        provider.getProviderCode(),
        definition.getModelCode(),
        provider.getDisplayName(),
        definition.getInputPricePer1k() == null ? 0.0 : definition.getInputPricePer1k(),
        String.valueOf(metadata.getOrDefault("transport", "BUILTIN")),
        provider.getApiEndpoint(),
        emptyToNull(String.valueOf(metadata.getOrDefault("apiKeyEnvVar", ""))),
        emptyToNull(String.valueOf(metadata.getOrDefault("apiVersion", ""))));
  }

  private EmbeddingVector embed(String text, ActiveEmbeddingSelection selection) {
    String normalized = text == null ? "" : text.trim();
    if (normalized.isBlank()) {
      return builtinEmbedding("");
    }

    return switch (selection.transport().toUpperCase(Locale.ROOT)) {
      case "OPENAI_COMPATIBLE" -> {
        String apiKey = resolveApiKey(selection.apiKeyEnvVar());
        if (apiKey == null || selection.apiEndpoint() == null || selection.apiEndpoint().isBlank()) {
          yield builtinEmbedding(normalized);
        }
        yield requestOpenAiCompatibleEmbedding(selection, normalized, apiKey);
      }
      case "QWEN_DASHSCOPE" -> {
        String apiKey = resolveApiKey(selection.apiKeyEnvVar());
        if (apiKey == null) {
          yield builtinEmbedding(normalized);
        }
        yield requestQwenCompatibleEmbedding(selection, normalized, apiKey);
      }
      case "AZURE_OPENAI" -> {
        String apiKey = resolveApiKey(selection.apiKeyEnvVar());
        if (apiKey == null || selection.apiEndpoint() == null || selection.apiEndpoint().isBlank()) {
          yield builtinEmbedding(normalized);
        }
        yield requestAzureEmbedding(selection, normalized, apiKey);
      }
      default -> builtinEmbedding(normalized);
    };
  }

  private EmbeddingVector requestOpenAiCompatibleEmbedding(
      ActiveEmbeddingSelection selection, String text, String apiKey) {
    try {
      HttpRequest request =
          HttpRequest.newBuilder()
              .uri(URI.create(resolveOpenAiCompatibleEmbeddingEndpoint(selection.apiEndpoint())))
              .timeout(Duration.ofSeconds(20))
              .header("Content-Type", "application/json")
              .header("Authorization", "Bearer " + apiKey)
              .POST(HttpRequest.BodyPublishers.ofString(buildOpenAiEmbeddingPayload(selection.modelCode(), text)))
              .build();
      HttpResponse<String> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
      if (response.statusCode() >= 400) {
        throw new IllegalStateException(
            "OPENAI_COMPATIBLE_EMBEDDING_HTTP_" + response.statusCode() + ": " + response.body());
      }
      return parseEmbeddingResponse(response.body(), "OPENAI_COMPATIBLE_EMBEDDING_PARSE_ERROR");
    } catch (IOException exception) {
      throw new IllegalStateException("OPENAI_COMPATIBLE_EMBEDDING_IO_ERROR", exception);
    } catch (InterruptedException exception) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException("OPENAI_COMPATIBLE_EMBEDDING_INTERRUPTED", exception);
    }
  }

  private EmbeddingVector requestQwenCompatibleEmbedding(
      ActiveEmbeddingSelection selection, String text, String apiKey) {
    try {
      HttpRequest request =
          HttpRequest.newBuilder()
              .uri(URI.create(resolveQwenCompatibleEmbeddingEndpoint(selection.apiEndpoint())))
              .timeout(Duration.ofSeconds(20))
              .header("Content-Type", "application/json")
              .header("Authorization", "Bearer " + apiKey)
              .POST(HttpRequest.BodyPublishers.ofString(buildOpenAiEmbeddingPayload(selection.modelCode(), text)))
              .build();
      HttpResponse<String> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
      if (response.statusCode() >= 400) {
        throw new IllegalStateException(
            "QWEN_DASHSCOPE_EMBEDDING_HTTP_" + response.statusCode() + ": " + response.body());
      }
      return parseEmbeddingResponse(response.body(), "QWEN_DASHSCOPE_EMBEDDING_PARSE_ERROR");
    } catch (IOException exception) {
      throw new IllegalStateException("QWEN_DASHSCOPE_EMBEDDING_IO_ERROR", exception);
    } catch (InterruptedException exception) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException("QWEN_DASHSCOPE_EMBEDDING_INTERRUPTED", exception);
    }
  }

  private EmbeddingVector requestAzureEmbedding(
      ActiveEmbeddingSelection selection, String text, String apiKey) {
    try {
      HttpRequest request =
          HttpRequest.newBuilder()
              .uri(
                  URI.create(
                      resolveAzureEmbeddingEndpoint(
                          selection.apiEndpoint(), selection.modelCode(), selection.apiVersion())))
              .timeout(Duration.ofSeconds(20))
              .header("Content-Type", "application/json")
              .header("api-key", apiKey)
              .POST(HttpRequest.BodyPublishers.ofString(buildAzureEmbeddingPayload(text)))
              .build();
      HttpResponse<String> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
      if (response.statusCode() >= 400) {
        throw new IllegalStateException(
            "AZURE_OPENAI_EMBEDDING_HTTP_" + response.statusCode() + ": " + response.body());
      }
      return parseEmbeddingResponse(response.body(), "AZURE_OPENAI_EMBEDDING_PARSE_ERROR");
    } catch (IOException exception) {
      throw new IllegalStateException("AZURE_OPENAI_EMBEDDING_IO_ERROR", exception);
    } catch (InterruptedException exception) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException("AZURE_OPENAI_EMBEDDING_INTERRUPTED", exception);
    }
  }

  private EmbeddingVector parseEmbeddingResponse(String body, String parseErrorCode) {
    try {
      JsonNode root = objectMapper.readTree(body);
      JsonNode embeddingNode = root.path("data").path(0).path("embedding");
      if (!embeddingNode.isArray() || embeddingNode.isEmpty()) {
        throw new IllegalStateException(parseErrorCode);
      }
      List<Double> values = new ArrayList<>();
      for (JsonNode node : embeddingNode) {
        values.add(objectMapper.convertValue(node, Double.class));
      }
      return new EmbeddingVector(values, objectMapper.writeValueAsString(values));
    } catch (JacksonException exception) {
      throw new IllegalStateException(parseErrorCode, exception);
    }
  }

  private EmbeddingVector builtinEmbedding(String text) {
    double[] values = new double[BUILTIN_VECTOR_SIZE];
    String normalized = text == null ? "" : text.toLowerCase(Locale.ROOT);
    List<String> tokens = tokenize(normalized);
    if (tokens.isEmpty()) {
      return serializeVector(values);
    }
    for (String token : tokens) {
      int tokenBucket = Math.floorMod(token.hashCode(), BUILTIN_VECTOR_SIZE);
      values[tokenBucket] += 1.0;
      for (int index = 0; index < token.length() - 1; index++) {
        String bigram = token.substring(index, index + 2);
        int bigramBucket = Math.floorMod(bigram.hashCode(), BUILTIN_VECTOR_SIZE);
        values[bigramBucket] += 0.5;
      }
    }
    return normalizeAndSerialize(values);
  }

  private List<String> tokenize(String text) {
    List<String> tokens = new ArrayList<>();
    for (String item : text.split("[^\\p{L}\\p{N}]+")) {
      if (!item.isBlank()) {
        tokens.add(item);
      }
    }
    String compact = text.replaceAll("[^\\p{L}\\p{N}]", "").trim();
    for (int index = 0; index < compact.length(); index++) {
      tokens.add(String.valueOf(compact.charAt(index)));
    }
    return tokens;
  }

  private EmbeddingVector normalizeAndSerialize(double[] rawValues) {
    double norm = 0.0;
    for (double value : rawValues) {
      norm += value * value;
    }
    if (norm == 0.0) {
      return serializeVector(rawValues);
    }
    double scale = Math.sqrt(norm);
    for (int index = 0; index < rawValues.length; index++) {
      rawValues[index] = rawValues[index] / scale;
    }
    return serializeVector(rawValues);
  }

  private EmbeddingVector serializeVector(double[] rawValues) {
    try {
      List<Double> values = new ArrayList<>(rawValues.length);
      for (double value : rawValues) {
        values.add(value);
      }
      return new EmbeddingVector(values, objectMapper.writeValueAsString(values));
    } catch (JacksonException exception) {
      throw new IllegalStateException("Failed to serialize built-in embedding vector", exception);
    }
  }

  private String buildOpenAiEmbeddingPayload(String modelCode, String text) throws JacksonException {
    return objectMapper
        .createObjectNode()
        .put("model", modelCode)
        .put("input", text)
        .toString();
  }

  private String buildAzureEmbeddingPayload(String text) throws JacksonException {
    return objectMapper.createObjectNode().put("input", text).toString();
  }

  private String resolveOpenAiCompatibleEmbeddingEndpoint(String apiEndpoint) {
    String trimmed = apiEndpoint == null ? "" : apiEndpoint.trim();
    if (trimmed.endsWith("/embeddings")) {
      return trimmed;
    }
    if (trimmed.endsWith("/")) {
      return trimmed + "embeddings";
    }
    return trimmed + "/embeddings";
  }

  private String resolveQwenCompatibleEmbeddingEndpoint(String apiEndpoint) {
    String trimmed = apiEndpoint == null ? "" : apiEndpoint.trim();
    if (trimmed.isBlank()) {
      return "https://dashscope.aliyuncs.com/compatible-mode/v1/embeddings";
    }
    if (trimmed.endsWith("/embeddings")) {
      return trimmed;
    }
    if (trimmed.endsWith("/")) {
      return trimmed + "embeddings";
    }
    return trimmed + "/embeddings";
  }

  private String resolveAzureEmbeddingEndpoint(String apiEndpoint, String deployment, String apiVersion) {
    String base = apiEndpoint == null ? "" : apiEndpoint.trim();
    String normalizedBase = base.endsWith("/") ? base.substring(0, base.length() - 1) : base;
    String encodedDeployment = URLEncoder.encode(deployment, StandardCharsets.UTF_8);
    String version =
        apiVersion == null || apiVersion.isBlank() ? "2024-02-15-preview" : apiVersion.trim();
    return normalizedBase
        + "/openai/deployments/"
        + encodedDeployment
        + "/embeddings?api-version="
        + URLEncoder.encode(version, StandardCharsets.UTF_8);
  }

  private int estimateTokens(String value) {
    if (value == null || value.isBlank()) {
      return 0;
    }
    return Math.max(1, (int) Math.ceil(value.trim().length() / 4.0));
  }

  private String hashReference(String value) {
    try {
      byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
      StringBuilder builder = new StringBuilder();
      for (int index = 0; index < 12; index++) {
        builder.append(String.format(Locale.ROOT, "%02x", digest[index]));
      }
      return builder.toString();
    } catch (NoSuchAlgorithmException exception) {
      throw new IllegalStateException("SHA-256 unavailable", exception);
    }
  }

  private String toJson(Map<String, Object> value) {
    try {
      return objectMapper.writeValueAsString(value);
    } catch (JacksonException exception) {
      throw new IllegalStateException("Failed to serialize embedding log metadata", exception);
    }
  }

  private Map<String, Object> fromJson(String value) {
    try {
      return objectMapper.readValue(value, new TypeReference<>() {});
    } catch (JacksonException exception) {
      throw new IllegalStateException("Failed to deserialize model provider metadata", exception);
    }
  }

  private String resolveApiKey(String apiKeyEnvVar) {
    if (apiKeyEnvVar == null || apiKeyEnvVar.isBlank()) {
      return null;
    }
    String envValue = System.getenv(apiKeyEnvVar);
    if (envValue != null && !envValue.isBlank()) {
      return envValue;
    }
    String propertyValue = System.getProperty("agentx.model." + apiKeyEnvVar);
    return propertyValue == null || propertyValue.isBlank() ? null : propertyValue;
  }

  private String emptyToNull(String value) {
    return value == null || value.isBlank() ? null : value;
  }

  public record EmbeddingResult(
      String embeddingRef,
      String embeddingJson,
      int dimensions,
      String providerCode,
      String modelCode,
      Long logId) {}

    public record QueryEmbeddingResult(
        String embeddingJson,
        int dimensions,
        String providerCode,
        String modelCode,
        Long logId) {}

  private record EmbeddingVector(List<Double> values, String serialized) {}

  private record ActiveEmbeddingSelection(
      String providerCode,
      String modelCode,
      String providerDisplayName,
      double inputPricePer1k,
      String transport,
      String apiEndpoint,
      String apiKeyEnvVar,
      String apiVersion) {}
}