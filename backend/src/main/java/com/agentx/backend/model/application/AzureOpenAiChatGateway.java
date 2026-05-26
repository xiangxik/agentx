package com.agentx.backend.model.application;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import org.springframework.stereotype.Service;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Service
public class AzureOpenAiChatGateway {

  private static final HttpClient HTTP_CLIENT =
      HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

  private final ObjectMapper objectMapper;

  public AzureOpenAiChatGateway(ObjectMapper objectMapper) {
    this.objectMapper = objectMapper;
  }

  public GatewayResponse complete(ChatRequest request) {
    try {
      HttpRequest httpRequest =
          HttpRequest.newBuilder()
              .uri(
                  URI.create(
                      resolveEndpoint(
                          request.apiEndpoint(), request.deployment(), request.apiVersion())))
              .timeout(Duration.ofSeconds(20))
              .header("Content-Type", "application/json")
              .header("api-key", request.apiKey())
              .POST(HttpRequest.BodyPublishers.ofString(buildPayload(request)))
              .build();

      HttpResponse<String> response =
          HTTP_CLIENT.send(httpRequest, HttpResponse.BodyHandlers.ofString());
      if (response.statusCode() >= 400) {
        throw new IllegalStateException(
            "AZURE_OPENAI_HTTP_" + response.statusCode() + ": " + response.body());
      }

      JsonNode root = objectMapper.readTree(response.body());
      JsonNode contentNode = root.path("choices").path(0).path("message").path("content");
      String content = contentNode.isMissingNode() ? "" : objectMapper.convertValue(contentNode, String.class);
      if (content == null || content.isBlank()) {
        throw new IllegalStateException("AZURE_OPENAI_EMPTY_CONTENT");
      }
      return new GatewayResponse(content.trim());
    } catch (IOException exception) {
      throw new IllegalStateException("AZURE_OPENAI_IO_ERROR", exception);
    } catch (InterruptedException exception) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException("AZURE_OPENAI_INTERRUPTED", exception);
    } catch (JacksonException exception) {
      throw new IllegalStateException("AZURE_OPENAI_PARSE_ERROR", exception);
    }
  }

  private String resolveEndpoint(String apiEndpoint, String deployment, String apiVersion) {
    String base = apiEndpoint == null ? "" : apiEndpoint.trim();
    String normalizedBase = base.endsWith("/") ? base.substring(0, base.length() - 1) : base;
    String encodedDeployment = URLEncoder.encode(deployment, StandardCharsets.UTF_8);
    String version =
        apiVersion == null || apiVersion.isBlank() ? "2024-02-15-preview" : apiVersion.trim();
    return normalizedBase
        + "/openai/deployments/"
        + encodedDeployment
        + "/chat/completions?api-version="
        + URLEncoder.encode(version, StandardCharsets.UTF_8);
  }

  private String buildPayload(ChatRequest request) throws JacksonException {
    String systemPrompt =
        request.knowledgeContext() == null || request.knowledgeContext().isBlank()
            ? "You are a concise customer support assistant."
            : "You are a concise customer support assistant. Answer strictly from the supplied knowledge context when possible.";
    String userPrompt =
        request.knowledgeContext() == null || request.knowledgeContext().isBlank()
            ? request.question()
            : "Knowledge context:\n"
                + request.knowledgeContext().trim()
                + "\n\nUser question:\n"
                + request.question();

    return objectMapper
        .createObjectNode()
        .put("temperature", 0.2)
        .put("max_tokens", 512)
        .set(
            "messages",
            objectMapper
                .createArrayNode()
                .add(
                    objectMapper
                        .createObjectNode()
                        .put("role", "system")
                        .put("content", systemPrompt))
                .add(
                    objectMapper
                        .createObjectNode()
                        .put("role", "user")
                        .put("content", userPrompt)))
        .toString();
  }

  public record ChatRequest(
      String apiEndpoint,
      String apiKey,
      String deployment,
      String apiVersion,
      String question,
      String knowledgeContext) {}

  public record GatewayResponse(String answer) {}
}