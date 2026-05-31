package com.agentx.backend.deployment.api;

import com.agentx.backend.common.security.SecurityUtils;
import com.agentx.backend.deployment.application.DeploymentChannelService;
import jakarta.validation.constraints.NotBlank;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/chatbots/{chatbotId}/deployment")
@PreAuthorize("hasAnyRole('SUPER_ADMIN','TENANT_ADMIN')")
public class DeploymentChannelAdminController {

  private final DeploymentChannelService deploymentChannelService;

  public DeploymentChannelAdminController(DeploymentChannelService deploymentChannelService) {
    this.deploymentChannelService = deploymentChannelService;
  }

  @GetMapping
  public DeploymentChannelService.DeploymentOverview overview(@PathVariable Long chatbotId) {
    return deploymentChannelService.getOverview(SecurityUtils.currentUser(), chatbotId);
  }

  @GetMapping("/domains")
  public List<DeploymentChannelService.WhitelistDomainSummary> listDomains(
      @PathVariable Long chatbotId) {
    return deploymentChannelService.listWhitelistDomains(
        SecurityUtils.currentUser(), chatbotId);
  }

  @PostMapping("/domains")
  public DeploymentChannelService.WhitelistDomainSummary addDomain(
      @PathVariable Long chatbotId, @RequestBody AddDomainRequest request) {
    return deploymentChannelService.addWhitelistDomain(
        SecurityUtils.currentUser(), chatbotId, request.domain());
  }

  @DeleteMapping("/domains/{domainId}")
  public void deleteDomain(@PathVariable Long chatbotId, @PathVariable Long domainId) {
    deploymentChannelService.deleteWhitelistDomain(
        SecurityUtils.currentUser(), chatbotId, domainId);
  }

  @ExceptionHandler(IllegalArgumentException.class)
  @ResponseStatus(HttpStatus.BAD_REQUEST)
  public Map<String, String> handleIllegalArgument(IllegalArgumentException exception) {
    if ("DOMAIN_REQUIRED".equals(exception.getMessage())
        || "DOMAIN_INVALID".equals(exception.getMessage())
        || "CHATBOT_NOT_FOUND".equals(exception.getMessage())) {
      return Map.of("code", exception.getMessage());
    }

    throw exception;
  }

  public record AddDomainRequest(@NotBlank String domain) {}
}