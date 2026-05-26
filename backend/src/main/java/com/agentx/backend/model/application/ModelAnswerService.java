package com.agentx.backend.model.application;

import com.agentx.backend.model.domain.ModelCallLog;
import com.agentx.backend.model.domain.ModelCallLogRepository;
import com.agentx.backend.model.domain.ModelCallStatus;
import com.agentx.backend.model.domain.ModelDefinition;
import com.agentx.backend.model.domain.ModelDefinitionRepository;
import com.agentx.backend.model.domain.ModelDefinitionStatus;
import com.agentx.backend.model.domain.ModelProvider;
import com.agentx.backend.model.domain.ModelProviderRepository;
import com.agentx.backend.model.domain.ModelPurpose;
import com.agentx.backend.plan.application.PlanService;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

@Service
public class ModelAnswerService {

  private static final String BUILTIN_PROVIDER_CODE = "AGENTX_BUILTIN";
  private static final String BUILTIN_CHAT_MODEL = "agentx-direct-reply-v1";
  private static final String BUILTIN_RAG_MODEL = "agentx-knowledge-reply-v1";

  private final ModelProviderRepository modelProviderRepository;
  private final ModelDefinitionRepository modelDefinitionRepository;
  private final ModelCallLogRepository modelCallLogRepository;
  private final PlanService planService;
  private final OpenAiCompatibleChatGateway openAiCompatibleChatGateway;
  private final AzureOpenAiChatGateway azureOpenAiChatGateway;
  private final AnthropicChatGateway anthropicChatGateway;
  private final QwenChatGateway qwenChatGateway;
  private final ObjectMapper objectMapper;

  public ModelAnswerService(
      ModelProviderRepository modelProviderRepository,
      ModelDefinitionRepository modelDefinitionRepository,
      ModelCallLogRepository modelCallLogRepository,
      PlanService planService,
      OpenAiCompatibleChatGateway openAiCompatibleChatGateway,
      AzureOpenAiChatGateway azureOpenAiChatGateway,
      AnthropicChatGateway anthropicChatGateway,
      QwenChatGateway qwenChatGateway,
      ObjectMapper objectMapper) {
    this.modelProviderRepository = modelProviderRepository;
    this.modelDefinitionRepository = modelDefinitionRepository;
    this.modelCallLogRepository = modelCallLogRepository;
    this.planService = planService;
    this.openAiCompatibleChatGateway = openAiCompatibleChatGateway;
    this.azureOpenAiChatGateway = azureOpenAiChatGateway;
    this.anthropicChatGateway = anthropicChatGateway;
    this.qwenChatGateway = qwenChatGateway;
    this.objectMapper = objectMapper;
  }

  @SuppressWarnings("checkstyle:MagicNumber")
  @Transactional
  public ModelAnswer generate(ModelAnswerRequest request) {
    String normalizedQuestion = normalize(request.question());
    ActiveModelSelection selection =
        resolveSelection(request.preferredProviderCode(), request.preferredModelCode(), request.knowledgeContext());
    int promptTokens = estimateTokens(request.question()) + estimateTokens(request.knowledgeContext());
    int estimatedTotalTokens = promptTokens + Math.max(24, promptTokens / 2);
    planService.ensureTenantWithinLimit(request.tenantId(), "tokens", estimatedTotalTokens);

    GeneratedModelOutput generatedOutput = tryGenerateWithSelection(selection, request, normalizedQuestion);
    if (generatedOutput != null) {
      return saveSuccessLog(request, selection, generatedOutput, promptTokens);
    }

    String fallbackMessage = "Provider call failed";
    GeneratedModelOutput failedExternalOutput =
        tryGenerateExternal(selection, request, 1, normalizedQuestion);
    if (failedExternalOutput != null) {
      return saveSuccessLog(request, selection, failedExternalOutput, promptTokens);
    }

    recordFailureLog(request, selection, promptTokens, fallbackMessage, 1);
    ActiveModelSelection builtinSelection = builtinSelection(request.knowledgeContext());
    GeneratedModelOutput builtinOutput = generateBuiltinOutput(request, normalizedQuestion, 0);
    return saveSuccessLog(request, builtinSelection, builtinOutput, promptTokens);
  }

  private ActiveModelSelection resolveSelection(
      String preferredProviderCode, String preferredModelCode, String knowledgeContext) {
    ActiveModelSelection preferredSelection =
        resolvePreferredSelection(preferredProviderCode, preferredModelCode);
    if (preferredSelection != null) {
      return preferredSelection;
    }

    List<ModelDefinition> activeModels =
        modelDefinitionRepository.findByStatusAndPurposeOrderByIsDefaultDescIdAsc(
            ModelDefinitionStatus.ACTIVE, ModelPurpose.CHAT_COMPLETION);
    if (activeModels.isEmpty()) {
      return new ActiveModelSelection(
          BUILTIN_PROVIDER_CODE,
          knowledgeContext == null || knowledgeContext.isBlank() ? BUILTIN_CHAT_MODEL : BUILTIN_RAG_MODEL,
          BUILTIN_PROVIDER_CODE,
          0.0,
          0.0,
          "BUILTIN",
          null,
          null,
          null);
    }

    ModelDefinition definition = activeModels.get(0);
    ModelProvider provider = modelProviderRepository.findById(definition.getProviderId()).orElseThrow();
    return toActiveSelection(provider, definition);
  }

  private ActiveModelSelection resolvePreferredSelection(
      String preferredProviderCode, String preferredModelCode) {
    if (preferredProviderCode == null
        || preferredProviderCode.isBlank()
        || preferredModelCode == null
        || preferredModelCode.isBlank()) {
      return null;
    }

    ModelProvider provider =
        modelProviderRepository.findByProviderCode(preferredProviderCode.trim()).orElse(null);
    if (provider == null) {
      return null;
    }

    ModelDefinition definition =
        modelDefinitionRepository.findByProviderIdOrderByIdAsc(provider.getId()).stream()
            .filter(candidate -> candidate.getStatus() == ModelDefinitionStatus.ACTIVE)
            .filter(candidate -> candidate.getPurpose() == ModelPurpose.CHAT_COMPLETION)
            .filter(candidate -> preferredModelCode.trim().equals(candidate.getModelCode()))
            .findFirst()
            .orElse(null);
    if (definition == null) {
      return null;
    }

    return toActiveSelection(provider, definition);
  }

  private ActiveModelSelection builtinSelection(String knowledgeContext) {
    return new ActiveModelSelection(
        BUILTIN_PROVIDER_CODE,
        knowledgeContext == null || knowledgeContext.isBlank() ? BUILTIN_CHAT_MODEL : BUILTIN_RAG_MODEL,
        BUILTIN_PROVIDER_CODE,
        0.0,
        0.0,
        "BUILTIN",
        null,
      null,
      null);
  }

  private GeneratedModelOutput tryGenerateWithSelection(
      ActiveModelSelection selection,
      ModelAnswerRequest request,
      String normalizedQuestion) {
    if ("OPENAI_COMPATIBLE".equalsIgnoreCase(selection.transport())
        || "AZURE_OPENAI".equalsIgnoreCase(selection.transport())
        || "ANTHROPIC".equalsIgnoreCase(selection.transport())
        || "QWEN_DASHSCOPE".equalsIgnoreCase(selection.transport())) {
      GeneratedModelOutput firstAttempt = tryGenerateExternal(selection, request, 0, normalizedQuestion);
      if (firstAttempt != null) {
        return firstAttempt;
      }
      return tryGenerateExternal(selection, request, 1, normalizedQuestion);
    }
    return generateBuiltinOutput(request, normalizedQuestion, 0);
  }

  private GeneratedModelOutput tryGenerateExternal(
      ActiveModelSelection selection,
      ModelAnswerRequest request,
      int retryCount,
      String normalizedQuestion) {
    String apiKey = resolveApiKey(selection.apiKeyEnvVar());
    boolean requiresEndpoint = !"QWEN_DASHSCOPE".equalsIgnoreCase(selection.transport());
    if (apiKey == null
        || (requiresEndpoint && (selection.apiEndpoint() == null || selection.apiEndpoint().isBlank()))) {
      return null;
    }

    Instant startedAt = Instant.now();
    try {
      String answer =
        switch (selection.transport().toUpperCase(Locale.ROOT)) {
        case "OPENAI_COMPATIBLE" ->
          openAiCompatibleChatGateway
            .complete(
              new OpenAiCompatibleChatGateway.ChatRequest(
                selection.apiEndpoint(),
                apiKey,
                selection.modelCode(),
                normalizedQuestion,
                request.knowledgeContext()))
            .answer();
        case "AZURE_OPENAI" ->
          azureOpenAiChatGateway
            .complete(
              new AzureOpenAiChatGateway.ChatRequest(
                selection.apiEndpoint(),
                apiKey,
                selection.modelCode(),
                selection.apiVersion(),
                normalizedQuestion,
                request.knowledgeContext()))
            .answer();
        case "ANTHROPIC" ->
          anthropicChatGateway
            .complete(
              new AnthropicChatGateway.ChatRequest(
                selection.apiEndpoint(),
                apiKey,
                selection.apiVersion(),
                selection.modelCode(),
                normalizedQuestion,
                request.knowledgeContext()))
            .answer();
        case "QWEN_DASHSCOPE" ->
          qwenChatGateway
            .complete(
              new QwenChatGateway.ChatRequest(
                selection.apiEndpoint(),
                apiKey,
                selection.modelCode(),
                normalizedQuestion,
                request.knowledgeContext()))
            .answer();
        default -> throw new IllegalStateException("UNSUPPORTED_TRANSPORT: " + selection.transport());
        };
      return new GeneratedModelOutput(
        answer, Duration.between(startedAt, Instant.now()).toMillis(), retryCount);
    } catch (RuntimeException exception) {
      recordFailureLog(
          request,
          selection,
          estimateTokens(request.question()) + estimateTokens(request.knowledgeContext()),
          exception.getMessage(),
          retryCount);
      return null;
    }
  }

  private GeneratedModelOutput generateBuiltinOutput(
      ModelAnswerRequest request, String normalizedQuestion, int retryCount) {
    Instant startedAt = Instant.now();
    String answer =
        request.knowledgeContext() == null || request.knowledgeContext().isBlank()
            ? buildDirectAnswer(request.chatbotName(), normalizedQuestion, request.language())
            : buildKnowledgeGroundedAnswer(request.knowledgeContext());
    return new GeneratedModelOutput(
        answer, Duration.between(startedAt, Instant.now()).toMillis(), retryCount);
  }

  private ModelAnswer saveSuccessLog(
      ModelAnswerRequest request,
      ActiveModelSelection selection,
      GeneratedModelOutput output,
      int promptTokens) {
    int completionTokens = estimateTokens(output.answer());
    int totalTokens = promptTokens + completionTokens;
    double estimatedCost =
        (promptTokens / 1000D) * selection.inputPricePer1k()
            + (completionTokens / 1000D) * selection.outputPricePer1k();

    Map<String, Object> metadata = new LinkedHashMap<>();
    metadata.put(
        "mode",
        request.knowledgeContext() == null || request.knowledgeContext().isBlank()
            ? "DIRECT"
            : "KNOWLEDGE_AUGMENTED");
    metadata.put("providerDisplayName", selection.providerDisplayName());
    metadata.put("transport", selection.transport());

    ModelCallLog savedLog =
        saveLog(
            request,
            selection,
            ModelCallStatus.SUCCESS,
            promptTokens,
            completionTokens,
            totalTokens,
            estimatedCost,
            output.retryCount(),
            output.latencyMs(),
            null,
            metadata);

    return new ModelAnswer(
        output.answer(),
        selection.providerCode(),
        selection.modelCode(),
        promptTokens,
        completionTokens,
        totalTokens,
        estimatedCost,
        savedLog.getId());
  }

  private void recordFailureLog(
      ModelAnswerRequest request,
      ActiveModelSelection selection,
      int promptTokens,
      String errorMessage,
      int retryCount) {
    Map<String, Object> metadata = new LinkedHashMap<>();
    metadata.put("mode", request.knowledgeContext() == null || request.knowledgeContext().isBlank() ? "DIRECT" : "KNOWLEDGE_AUGMENTED");
    metadata.put("providerDisplayName", selection.providerDisplayName());
    metadata.put("transport", selection.transport());
    metadata.put("fallbackToBuiltin", true);
    saveLog(
        request,
        selection,
        ModelCallStatus.FAILED,
        promptTokens,
        0,
        promptTokens,
        0.0,
        retryCount,
        0,
        errorMessage,
        metadata);
  }

  private ModelCallLog saveLog(
      ModelAnswerRequest request,
      ActiveModelSelection selection,
      ModelCallStatus status,
      int promptTokens,
      int completionTokens,
      int totalTokens,
      double estimatedCost,
      int retryCount,
      long latencyMs,
      String errorMessage,
      Map<String, Object> metadata) {
    ModelCallLog log = new ModelCallLog();
    log.setTenantId(request.tenantId());
    log.setChatbotId(request.chatbotId());
    log.setConversationId(request.conversationId());
    log.setProviderCode(selection.providerCode());
    log.setModelCode(selection.modelCode());
    log.setPurpose(ModelPurpose.CHAT_COMPLETION);
    log.setStatus(status);
    log.setPromptTokens(promptTokens);
    log.setCompletionTokens(completionTokens);
    log.setTotalTokens(totalTokens);
    log.setEstimatedCost(estimatedCost);
    log.setRetryCount(retryCount);
    log.setLatencyMs(latencyMs);
    log.setErrorMessage(errorMessage);
    log.setMetadataJson(toJson(metadata));
    return modelCallLogRepository.save(log);
  }

  private String buildKnowledgeGroundedAnswer(String knowledgeContext) {
    return "根据知识库内容，" + knowledgeContext.trim();
  }

  private String buildDirectAnswer(String chatbotName, String normalizedQuestion, String language) {
    String assistantName =
        chatbotName == null || chatbotName.isBlank() ? "智能助理" : chatbotName.trim();
    String locale = language == null || language.isBlank() ? "zh-CN" : language;
    if (locale.toLowerCase(Locale.ROOT).startsWith("en")) {
      return "Based on your question, "
          + assistantName
          + " suggests clarifying the request first and then routing it to the right follow-up owner.";
    }

    if (normalizedQuestion.isBlank()) {
      return assistantName + "建议你补充更具体的问题背景，我再继续帮你分析。";
    }

    return assistantName + "的初步建议是：先确认问题范围，再补充订单、账号或业务场景等关键信息，以便继续处理“"
        + normalizedQuestion
        + "”。";
  }

  private int estimateTokens(String value) {
    if (value == null || value.isBlank()) {
      return 0;
    }
    return Math.max(1, (int) Math.ceil(value.trim().length() / 4.0));
  }

  private String normalize(String value) {
    return value == null ? "" : value.trim();
  }

  private String toJson(Map<String, Object> value) {
    try {
      return objectMapper.writeValueAsString(value);
    } catch (JacksonException exception) {
      throw new IllegalStateException("Failed to serialize model log metadata", exception);
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

  private ActiveModelSelection toActiveSelection(ModelProvider provider, ModelDefinition definition) {
    Map<String, Object> metadata = fromJson(provider.getMetadataJson());
    return new ActiveModelSelection(
        provider.getProviderCode(),
        definition.getModelCode(),
        provider.getDisplayName(),
        definition.getInputPricePer1k() == null ? 0.0 : definition.getInputPricePer1k(),
        definition.getOutputPricePer1k() == null ? 0.0 : definition.getOutputPricePer1k(),
        String.valueOf(metadata.getOrDefault("transport", "BUILTIN")),
        provider.getApiEndpoint(),
        emptyToNull(String.valueOf(metadata.getOrDefault("apiKeyEnvVar", ""))),
        emptyToNull(String.valueOf(metadata.getOrDefault("apiVersion", ""))));
  }

  public record ModelAnswerRequest(
      Long tenantId,
      Long chatbotId,
      Long conversationId,
      String chatbotName,
      String language,
      String question,
      String knowledgeContext,
      String preferredProviderCode,
      String preferredModelCode) {}

  public record ModelAnswer(
      String answer,
      String provider,
      String model,
      int promptTokens,
      int completionTokens,
      int totalTokens,
      double estimatedCost,
      Long logId) {}

  private record ActiveModelSelection(
      String providerCode,
      String modelCode,
      String providerDisplayName,
      double inputPricePer1k,
      double outputPricePer1k,
      String transport,
      String apiEndpoint,
      String apiKeyEnvVar,
      String apiVersion) {}

    private record GeneratedModelOutput(String answer, long latencyMs, int retryCount) {}
}