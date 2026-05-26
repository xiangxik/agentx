package com.agentx.backend.model.application;

import com.alibaba.dashscope.aigc.generation.Generation;
import com.alibaba.dashscope.aigc.generation.GenerationOutput;
import com.alibaba.dashscope.aigc.generation.GenerationResult;
import com.alibaba.dashscope.aigc.generation.models.QwenParam;
import com.alibaba.dashscope.common.Message;
import com.alibaba.dashscope.common.Role;
import com.alibaba.dashscope.exception.ApiException;
import com.alibaba.dashscope.exception.InputRequiredException;
import com.alibaba.dashscope.exception.NoApiKeyException;
import com.alibaba.dashscope.protocol.Protocol;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class QwenChatGateway {

  public GatewayResponse complete(ChatRequest request) {
    try {
      Generation generation =
          request.apiEndpoint() == null || request.apiEndpoint().isBlank()
              ? new Generation()
              : new Generation(Protocol.HTTP.getValue(), request.apiEndpoint().trim());
      GenerationResult result = generation.call(buildParam(request));
      String answer = extractAnswer(result);
      if (answer == null || answer.isBlank()) {
        throw new IllegalStateException("QWEN_DASHSCOPE_EMPTY_CONTENT");
      }
      return new GatewayResponse(answer.trim());
    } catch (ApiException exception) {
      throw new IllegalStateException("QWEN_DASHSCOPE_API_ERROR: " + exception.getMessage(), exception);
    } catch (NoApiKeyException exception) {
      throw new IllegalStateException("QWEN_DASHSCOPE_NO_API_KEY", exception);
    } catch (InputRequiredException exception) {
      throw new IllegalStateException("QWEN_DASHSCOPE_INPUT_ERROR", exception);
    }
  }

  private QwenParam buildParam(ChatRequest request) {
    List<Message> messages = new ArrayList<>();
    messages.add(
        Message.builder()
            .role(Role.SYSTEM.getValue())
            .content(systemPrompt(request.knowledgeContext()))
            .build());
    messages.add(
        Message.builder()
            .role(Role.USER.getValue())
            .content(userPrompt(request.question(), request.knowledgeContext()))
            .build());

    return QwenParam.builder()
        .apiKey(request.apiKey())
        .model(request.modelCode())
        .messages(messages)
        .resultFormat("message")
        .temperature(0.2f)
        .maxTokens(512)
        .build();
  }

  private String extractAnswer(GenerationResult result) {
    if (result == null || result.getOutput() == null) {
      return null;
    }

    GenerationOutput output = result.getOutput();
    if (output.getChoices() != null
        && !output.getChoices().isEmpty()
        && output.getChoices().get(0) != null
        && output.getChoices().get(0).getMessage() != null) {
      return output.getChoices().get(0).getMessage().getContent();
    }
    return output.getText();
  }

  private String systemPrompt(String knowledgeContext) {
    return knowledgeContext == null || knowledgeContext.isBlank()
        ? "You are a concise customer support assistant."
        : "You are a concise customer support assistant. Answer strictly from the supplied knowledge context when possible.";
  }

  private String userPrompt(String question, String knowledgeContext) {
    return knowledgeContext == null || knowledgeContext.isBlank()
        ? question
        : "Knowledge context:\n"
            + knowledgeContext.trim()
            + "\n\nUser question:\n"
            + question;
  }

  public record ChatRequest(
      String apiEndpoint,
      String apiKey,
      String modelCode,
      String question,
      String knowledgeContext) {}

  public record GatewayResponse(String answer) {}
}