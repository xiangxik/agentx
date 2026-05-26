package com.agentx.backend.model.application;

import com.agentx.backend.audit.application.AuditLogService;
import com.agentx.backend.common.security.CurrentUser;
import com.agentx.backend.model.domain.ModelDefinition;
import com.agentx.backend.model.domain.ModelDefinitionRepository;
import com.agentx.backend.model.domain.ModelDefinitionStatus;
import com.agentx.backend.model.domain.ModelProvider;
import com.agentx.backend.model.domain.ModelProviderRepository;
import com.agentx.backend.model.domain.ModelProviderStatus;
import com.agentx.backend.model.domain.ModelPurpose;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Locale;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Service
public class ModelProviderAdminService {

  private static final HttpClient HTTP_CLIENT =
      HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

  private final ModelProviderRepository modelProviderRepository;
  private final ModelDefinitionRepository modelDefinitionRepository;
  private final AuditLogService auditLogService;
  private final ObjectMapper objectMapper;

  public ModelProviderAdminService(
      ModelProviderRepository modelProviderRepository,
      ModelDefinitionRepository modelDefinitionRepository,
      AuditLogService auditLogService,
      ObjectMapper objectMapper) {
    this.modelProviderRepository = modelProviderRepository;
    this.modelDefinitionRepository = modelDefinitionRepository;
    this.auditLogService = auditLogService;
    this.objectMapper = objectMapper;
  }

  @Transactional(readOnly = true)
  public List<ModelProviderSummary> listProviders() {
    return modelProviderRepository.findAll().stream().map(this::toProviderSummary).toList();
  }

  @Transactional
  public ModelProviderSummary createProvider(CurrentUser actor, CreateProviderRequest request) {
    ModelProvider provider = new ModelProvider();
    provider.setProviderCode(request.providerCode());
    provider.setDisplayName(request.displayName());
    provider.setApiEndpoint(request.apiEndpoint());
    provider.setApiKeyHint(maskApiKey(request.apiKey()));
    provider.setStatus(ModelProviderStatus.valueOf(request.status()));
    provider.setMetadataJson(
      toJson(
        Map.of(
          "managedBy", "admin",
          "supports", request.supports() == null ? "" : request.supports(),
          "transport", request.transport() == null ? "BUILTIN" : request.transport(),
          "apiKeyEnvVar",
            request.apiKeyEnvVar() == null ? "" : request.apiKeyEnvVar().trim(),
          "apiVersion",
            request.apiVersion() == null ? "" : request.apiVersion().trim())));
    ModelProvider saved = modelProviderRepository.save(provider);
    auditLogService.record(
        null,
        actor.userId(),
        "MODEL_PROVIDER_CREATED",
        "MODEL_PROVIDER",
        String.valueOf(saved.getId()),
        "SUCCESS",
        "LOW",
        Map.of("providerCode", saved.getProviderCode()));
    return toProviderSummary(saved);
  }

  @Transactional
  public ModelProviderSummary updateProviderStatus(
      CurrentUser actor, Long providerId, ModelProviderStatus status) {
    ModelProvider provider = modelProviderRepository.findById(providerId).orElseThrow();
    provider.setStatus(status);
    provider.setUpdatedAt(Instant.now());
    auditLogService.record(
        null,
        actor.userId(),
        "MODEL_PROVIDER_STATUS_UPDATED",
        "MODEL_PROVIDER",
        String.valueOf(providerId),
        "SUCCESS",
        "LOW",
        Map.of("status", status.name()));
    return toProviderSummary(provider);
  }

  @Transactional(readOnly = true)
  public List<ModelDefinitionSummary> listModels(ModelPurpose purpose) {
    List<ModelDefinition> definitions =
        purpose == null
            ? modelDefinitionRepository.findAll()
            : modelDefinitionRepository.findByPurposeOrderByIdAsc(purpose);
    return definitions.stream().map(this::toModelDefinitionSummary).toList();
  }

  @Transactional(readOnly = true)
  public List<AvailableModelOption> listAvailableModels(Long providerId) {
    ModelProvider provider = modelProviderRepository.findById(providerId).orElseThrow();
    Map<String, Object> metadata = fromJson(provider.getMetadataJson());
    String transport = String.valueOf(metadata.getOrDefault("transport", "BUILTIN"));
    return switch (transport) {
      case "OPENAI_COMPATIBLE" -> fetchOpenAiCompatibleModels(provider, metadata);
      case "QWEN_DASHSCOPE" -> fetchDashScopeModels(provider, metadata);
      case "ANTHROPIC" ->
          List.of(
              new AvailableModelOption("claude-3-5-haiku-latest", "Claude 3.5 Haiku"),
              new AvailableModelOption("claude-3-5-sonnet-latest", "Claude 3.5 Sonnet"),
              new AvailableModelOption("claude-3-opus-latest", "Claude 3 Opus"));
      case "BUILTIN" ->
          List.of(
              new AvailableModelOption("agentx-direct-reply-v1", "AgentX Direct Reply"),
              new AvailableModelOption("agentx-knowledge-reply-v1", "AgentX Knowledge Reply"));
      default -> List.of();
    };
  }

  @Transactional
  public ModelDefinitionSummary createModel(
      CurrentUser actor, Long providerId, CreateModelRequest request) {
    ModelProvider provider = modelProviderRepository.findById(providerId).orElseThrow();
    validatePurposeTransportCompatibility(provider, request.purpose());
    if (request.isDefault()) {
      clearDefault(request.purpose());
    }

    ModelDefinition definition = new ModelDefinition();
    definition.setProviderId(providerId);
    definition.setModelCode(request.modelCode());
    definition.setDisplayName(request.displayName());
    definition.setPurpose(ModelPurpose.valueOf(request.purpose()));
    definition.setStatus(ModelDefinitionStatus.valueOf(request.status()));
    definition.setDefault(request.isDefault());
    definition.setInputPricePer1k(request.inputPricePer1k());
    definition.setOutputPricePer1k(request.outputPricePer1k());
    definition.setConfigJson(toJson(Map.of("maxTokens", request.maxTokens())));
    ModelDefinition saved = modelDefinitionRepository.save(definition);
    auditLogService.record(
        null,
        actor.userId(),
        "MODEL_DEFINITION_CREATED",
        "MODEL_DEFINITION",
        String.valueOf(saved.getId()),
        "SUCCESS",
        "LOW",
        Map.of("providerId", providerId, "purpose", request.purpose()));
    return toModelDefinitionSummary(saved);
  }

  private void validatePurposeTransportCompatibility(ModelProvider provider, String purpose) {
    ModelPurpose requestedPurpose = ModelPurpose.valueOf(purpose);
    if (requestedPurpose != ModelPurpose.EMBEDDING) {
      return;
    }

    Map<String, Object> metadata = fromJson(provider.getMetadataJson());
    String transport = String.valueOf(metadata.getOrDefault("transport", "BUILTIN")).toUpperCase(Locale.ROOT);
    if ("BUILTIN".equals(transport)
        || "OPENAI_COMPATIBLE".equals(transport)
        || "AZURE_OPENAI".equals(transport)
        || "QWEN_DASHSCOPE".equals(transport)) {
      return;
    }
    throw new IllegalArgumentException("EMBEDDING_TRANSPORT_UNSUPPORTED");
  }

  @Transactional
  public ModelDefinitionSummary updateModelStatus(
      CurrentUser actor, Long modelId, ModelDefinitionStatus status) {
    ModelDefinition definition = modelDefinitionRepository.findById(modelId).orElseThrow();
    definition.setStatus(status);
    definition.setUpdatedAt(Instant.now());
    if (status != ModelDefinitionStatus.ACTIVE) {
      definition.setDefault(false);
    }
    auditLogService.record(
        null,
        actor.userId(),
        "MODEL_DEFINITION_STATUS_UPDATED",
        "MODEL_DEFINITION",
        String.valueOf(modelId),
        "SUCCESS",
        "LOW",
        Map.of("status", status.name()));
    return toModelDefinitionSummary(definition);
  }

  @Transactional
  public ModelDefinitionSummary setDefaultModel(CurrentUser actor, Long modelId) {
    ModelDefinition definition = modelDefinitionRepository.findById(modelId).orElseThrow();
    clearDefault(definition.getPurpose().name());
    definition.setDefault(true);
    definition.setUpdatedAt(Instant.now());
    auditLogService.record(
        null,
        actor.userId(),
        "MODEL_DEFINITION_DEFAULT_UPDATED",
        "MODEL_DEFINITION",
        String.valueOf(modelId),
        "SUCCESS",
        "LOW",
        Map.of("purpose", definition.getPurpose().name()));
    return toModelDefinitionSummary(definition);
  }

  private void clearDefault(String purpose) {
    modelDefinitionRepository
        .findByStatusAndPurposeOrderByIsDefaultDescIdAsc(
            ModelDefinitionStatus.ACTIVE, ModelPurpose.valueOf(purpose))
        .forEach(
            definition -> {
              if (definition.isDefault()) {
                definition.setDefault(false);
                definition.setUpdatedAt(Instant.now());
              }
            });
  }

  private ModelProviderSummary toProviderSummary(ModelProvider provider) {
    Map<String, Object> metadata = fromJson(provider.getMetadataJson());
    return new ModelProviderSummary(
        provider.getId(),
        provider.getProviderCode(),
        provider.getDisplayName(),
        provider.getApiEndpoint(),
        provider.getApiKeyHint(),
        provider.getStatus().name(),
      String.valueOf(metadata.getOrDefault("supports", "")),
      String.valueOf(metadata.getOrDefault("transport", "BUILTIN")),
      emptyToNull(String.valueOf(metadata.getOrDefault("apiKeyEnvVar", ""))),
      emptyToNull(String.valueOf(metadata.getOrDefault("apiVersion", ""))));
  }

  private ModelDefinitionSummary toModelDefinitionSummary(ModelDefinition definition) {
    ModelProvider provider = modelProviderRepository.findById(definition.getProviderId()).orElseThrow();
    Map<String, Object> config = fromJson(definition.getConfigJson());
    return new ModelDefinitionSummary(
        definition.getId(),
        definition.getProviderId(),
        provider.getProviderCode(),
        definition.getModelCode(),
        definition.getDisplayName(),
        definition.getPurpose().name(),
        definition.getStatus().name(),
        definition.isDefault(),
        definition.getInputPricePer1k(),
        definition.getOutputPricePer1k(),
        ((Number) config.getOrDefault("maxTokens", 0)).intValue());
  }

  private List<AvailableModelOption> fetchOpenAiCompatibleModels(
      ModelProvider provider, Map<String, Object> metadata) {
    String apiKey = resolveApiKey(emptyToNull(String.valueOf(metadata.getOrDefault("apiKeyEnvVar", ""))));
    String apiEndpoint = provider.getApiEndpoint();
    if (apiKey == null || apiEndpoint == null || apiEndpoint.isBlank()) {
      return List.of();
    }

    try {
      HttpRequest request =
          HttpRequest.newBuilder()
              .uri(URI.create(resolveModelsEndpoint(apiEndpoint)))
              .timeout(Duration.ofSeconds(20))
              .header("Authorization", "Bearer " + apiKey)
              .header("Content-Type", "application/json")
              .GET()
              .build();
      HttpResponse<String> response =
          HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
      if (response.statusCode() >= 400) {
        throw new IllegalStateException(
            "OPENAI_COMPATIBLE_MODELS_HTTP_" + response.statusCode() + ": " + response.body());
      }

      JsonNode root = objectMapper.readTree(response.body());
      JsonNode data = root.path("data");
      if (!data.isArray()) {
        return List.of();
      }

      List<AvailableModelOption> models = new ArrayList<>();
      for (JsonNode node : data) {
        String modelCode = emptyToNull(objectMapper.convertValue(node.path("id"), String.class));
        if (modelCode == null) {
          continue;
        }
        String displayName = emptyToNull(objectMapper.convertValue(node.path("name"), String.class));
        models.add(new AvailableModelOption(modelCode, displayName == null ? modelCode : displayName));
      }
      return models;
    } catch (IOException exception) {
      throw new IllegalStateException("OPENAI_COMPATIBLE_MODELS_IO_ERROR", exception);
    } catch (InterruptedException exception) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException("OPENAI_COMPATIBLE_MODELS_INTERRUPTED", exception);
    } catch (JacksonException exception) {
      throw new IllegalStateException("OPENAI_COMPATIBLE_MODELS_PARSE_ERROR", exception);
    }
  }

  private List<AvailableModelOption> fetchDashScopeModels(
      ModelProvider provider, Map<String, Object> metadata) {
    String apiKey = resolveApiKey(emptyToNull(String.valueOf(metadata.getOrDefault("apiKeyEnvVar", ""))));
    if (apiKey == null) {
      return List.of();
    }

    try {
      HttpRequest request =
          HttpRequest.newBuilder()
              .uri(URI.create(resolveDashScopeModelsEndpoint(provider.getApiEndpoint())))
              .timeout(Duration.ofSeconds(20))
              .header("Authorization", "Bearer " + apiKey)
              .header("Content-Type", "application/json")
              .GET()
              .build();
      HttpResponse<String> response =
          HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
      if (response.statusCode() >= 400) {
        throw new IllegalStateException(
            "QWEN_DASHSCOPE_MODELS_HTTP_" + response.statusCode() + ": " + response.body());
      }

      JsonNode root = objectMapper.readTree(response.body());
      JsonNode data = root.path("data");
      if (!data.isArray()) {
        data = root.path("models");
      }
      if (!data.isArray()) {
        return List.of();
      }

      List<AvailableModelOption> models = new ArrayList<>();
      for (JsonNode node : data) {
        String modelCode = emptyToNull(objectMapper.convertValue(node.path("id"), String.class));
        if (modelCode == null) {
          continue;
        }
        String normalizedCode = modelCode.toLowerCase();
        if (!normalizedCode.startsWith("qwen")
            && !normalizedCode.startsWith("qwq")
            && !normalizedCode.startsWith("qvq")) {
          continue;
        }
        String displayName = emptyToNull(objectMapper.convertValue(node.path("name"), String.class));
        models.add(new AvailableModelOption(modelCode, displayName == null ? modelCode : displayName));
      }
      return models;
    } catch (IOException exception) {
      throw new IllegalStateException("QWEN_DASHSCOPE_MODELS_IO_ERROR", exception);
    } catch (InterruptedException exception) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException("QWEN_DASHSCOPE_MODELS_INTERRUPTED", exception);
    } catch (JacksonException exception) {
      throw new IllegalStateException("QWEN_DASHSCOPE_MODELS_PARSE_ERROR", exception);
    }
  }

  private String resolveModelsEndpoint(String apiEndpoint) {
    String trimmed = apiEndpoint == null ? "" : apiEndpoint.trim();
    if (trimmed.endsWith("/models")) {
      return trimmed;
    }
    if (trimmed.endsWith("/")) {
      return trimmed + "models";
    }
    return trimmed + "/models";
  }

  private String resolveDashScopeModelsEndpoint(String apiEndpoint) {
    if (apiEndpoint == null || apiEndpoint.isBlank()) {
      return "https://dashscope.aliyuncs.com/api/v1/models";
    }

    String trimmed = apiEndpoint.trim();
    if (trimmed.endsWith("/api/v1/models") || trimmed.endsWith("/models")) {
      return trimmed;
    }

    try {
      URI uri = URI.create(trimmed);
      String authority = uri.getAuthority() == null ? "" : uri.getAuthority();
      String base = uri.getScheme() + "://" + authority;
      String path = uri.getPath() == null ? "" : uri.getPath();
      if (path.contains("/compatible-mode/")) {
        return base + "/api/v1/models";
      }
      if (path.endsWith("/api/v1")) {
        return base + path + "/models";
      }
      if (path.isBlank() || "/".equals(path)) {
        return base + "/api/v1/models";
      }
      return base + (path.endsWith("/") ? path.substring(0, path.length() - 1) : path) + "/models";
    } catch (IllegalArgumentException exception) {
      throw new IllegalStateException("QWEN_DASHSCOPE_MODELS_INVALID_ENDPOINT", exception);
    }
  }

  private String maskApiKey(String apiKey) {
    if (apiKey == null || apiKey.isBlank()) {
      return null;
    }
    String trimmed = apiKey.trim();
    return trimmed.length() <= 4 ? "****" : "****" + trimmed.substring(trimmed.length() - 4);
  }

  private String emptyToNull(String value) {
    return value == null || value.isBlank() ? null : value;
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

  private String toJson(Map<String, ?> value) {
    try {
      return objectMapper.writeValueAsString(value);
    } catch (JacksonException exception) {
      throw new IllegalStateException("Failed to serialize model config", exception);
    }
  }

  private Map<String, Object> fromJson(String value) {
    try {
      return objectMapper.readValue(value, new TypeReference<>() {});
    } catch (JacksonException exception) {
      throw new IllegalStateException("Failed to deserialize model config", exception);
    }
  }

  public record CreateProviderRequest(
      String providerCode,
      String displayName,
      String apiEndpoint,
      String apiKey,
      String status,
      String supports,
      String transport,
      String apiKeyEnvVar,
      String apiVersion) {}

  public record CreateModelRequest(
      String modelCode,
      String displayName,
      String purpose,
      String status,
      boolean isDefault,
      Double inputPricePer1k,
      Double outputPricePer1k,
      int maxTokens) {}

  public record ModelProviderSummary(
      Long id,
      String providerCode,
      String displayName,
      String apiEndpoint,
      String apiKeyHint,
      String status,
      String supports,
      String transport,
      String apiKeyEnvVar,
      String apiVersion) {}

      public record AvailableModelOption(String modelCode, String displayName) {}

  public record ModelDefinitionSummary(
      Long id,
      Long providerId,
      String providerCode,
      String modelCode,
      String displayName,
      String purpose,
      String status,
      boolean isDefault,
      Double inputPricePer1k,
      Double outputPricePer1k,
      int maxTokens) {}
}