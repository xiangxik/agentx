package com.agentx.backend.model.application;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import org.springframework.stereotype.Service;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Service
public class AnthropicChatGateway {

  private static final HttpClient HTTP_CLIENT =
      HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

  private final ObjectMapper objectMapper;

  public AnthropicChatGateway(ObjectMapper objectMapper) {
    this.objectMapper = objectMapper;
  }

  public GatewayResponse complete(ChatRequest request) {
    try {
      HttpRequest httpRequest =
          HttpRequest.newBuilder()
              .uri(URI.create(resolveEndpoint(request.apiEndpoint())))
              .timeout(Duration.ofSeconds(20))
              .header("Content-Type", "application/json")
              .header("x-api-key", request.apiKey())
              .header(
                  "anthropic-version",
                  request.apiVersion() == null || request.apiVersion().isBlank()
                      ? "2023-06-01"
                      : request.apiVersion())
              .POST(HttpRequest.BodyPublishers.ofString(buildPayload(request)))
              .build();

      HttpResponse<String> response =
          HTTP_CLIENT.send(httpRequest, HttpResponse.BodyHandlers.ofString());
      if (response.statusCode() >= 400) {
        throw new IllegalStateException(
            "ANTHROPIC_HTTP_" + response.statusCode() + ": " + response.body());
      }

      JsonNode root = objectMapper.readTree(response.body());
      JsonNode contentNode = root.path("content").path(0).path("text");
      String content = contentNode.isMissingNode() ? "" : objectMapper.convertValue(contentNode, String.class);
      if (content == null || content.isBlank()) {
        throw new IllegalStateException("ANTHROPIC_EMPTY_CONTENT");
      }
      return new GatewayResponse(content.trim());
    } catch (IOException exception) {
      throw new IllegalStateException("ANTHROPIC_IO_ERROR", exception);
    } catch (InterruptedException exception) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException("ANTHROPIC_INTERRUPTED", exception);
    } catch (JacksonException exception) {
      throw new IllegalStateException("ANTHROPIC_PARSE_ERROR", exception);
    }
  }

  private String resolveEndpoint(String apiEndpoint) {
    String trimmed =
        apiEndpoint == null || apiEndpoint.isBlank() ? "https://api.anthropic.com" : apiEndpoint.trim();
    if (trimmed.endsWith("/v1/messages")) {
      return trimmed;
    }
    if (trimmed.endsWith("/")) {
      return trimmed + "v1/messages";
    }
    return trimmed + "/v1/messages";
  }

  private String buildPayload(ChatRequest request) throws JacksonException {
    String userPrompt =
        request.knowledgeContext() == null || request.knowledgeContext().isBlank()
            ? request.question()
            : "Knowledge context:\n"
                + request.knowledgeContext().trim()
                + "\n\nUser question:\n"
                + request.question();

    return objectMapper
        .createObjectNode()
        .put("model", request.modelCode())
        .put("max_tokens", 512)
        .put("system", "You are a concise customer support assistant.")
        .set(
            "messages",
            objectMapper
                .createArrayNode()
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
      String apiVersion,
      String modelCode,
      String question,
      String knowledgeContext) {}

  public record GatewayResponse(String answer) {}
}