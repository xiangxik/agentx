package com.agentx.backend.chatbot.api;

import com.agentx.backend.chatbot.application.ChatbotService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/public/chatbots")
public class PublicChatbotController {

  private final ChatbotService chatbotService;

  public PublicChatbotController(ChatbotService chatbotService) {
    this.chatbotService = chatbotService;
  }

  @GetMapping("/{publicCode}/snapshot")
  public ChatbotService.PublicChatbotSnapshot snapshot(@PathVariable String publicCode) {
    return chatbotService.getPublicSnapshot(publicCode);
  }
}
