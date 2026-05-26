package com.agentx.backend.model.api;

import com.agentx.backend.common.security.SecurityUtils;
import com.agentx.backend.model.application.ModelProviderAdminService;
import com.agentx.backend.model.domain.ModelDefinitionStatus;
import com.agentx.backend.model.domain.ModelProviderStatus;
import com.agentx.backend.model.domain.ModelPurpose;
import jakarta.validation.constraints.NotBlank;
import java.util.List;
import java.util.Map;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/model-providers")
public class ModelProviderAdminController {

  private final ModelProviderAdminService modelProviderAdminService;

  public ModelProviderAdminController(ModelProviderAdminService modelProviderAdminService) {
    this.modelProviderAdminService = modelProviderAdminService;
  }

  @GetMapping
  @PreAuthorize("hasAnyRole('SUPER_ADMIN','TENANT_ADMIN')")
  public List<ModelProviderAdminService.ModelProviderSummary> listProviders() {
    return modelProviderAdminService.listProviders();
  }

  @PostMapping
  @PreAuthorize("hasRole('SUPER_ADMIN')")
  public ModelProviderAdminService.ModelProviderSummary createProvider(
      @RequestBody CreateProviderRequest request) {
    return modelProviderAdminService.createProvider(
        SecurityUtils.currentUser(),
        new ModelProviderAdminService.CreateProviderRequest(
            request.providerCode(),
            request.displayName(),
            request.apiEndpoint(),
            request.apiKey(),
            request.status(),
          request.supports(),
          request.transport(),
          request.apiKeyEnvVar(),
          request.apiVersion()));
  }

  @PatchMapping("/{providerId}/status")
  @PreAuthorize("hasRole('SUPER_ADMIN')")
  public ModelProviderAdminService.ModelProviderSummary updateProviderStatus(
      @PathVariable Long providerId, @RequestBody UpdateProviderStatusRequest request) {
    return modelProviderAdminService.updateProviderStatus(
        SecurityUtils.currentUser(), providerId, ModelProviderStatus.valueOf(request.status()));
  }

  @GetMapping("/models")
  @PreAuthorize("hasAnyRole('SUPER_ADMIN','TENANT_ADMIN')")
  public List<ModelProviderAdminService.ModelDefinitionSummary> listModels(
      @RequestParam(required = false) String purpose) {
    return modelProviderAdminService.listModels(
        purpose == null || purpose.isBlank() ? null : ModelPurpose.valueOf(purpose));
  }

  @GetMapping("/{providerId}/available-models")
  @PreAuthorize("hasAnyRole('SUPER_ADMIN','TENANT_ADMIN')")
  public List<ModelProviderAdminService.AvailableModelOption> listAvailableModels(
      @PathVariable Long providerId) {
    return modelProviderAdminService.listAvailableModels(providerId);
  }

  @PostMapping("/{providerId}/models")
  @PreAuthorize("hasRole('SUPER_ADMIN')")
  public ModelProviderAdminService.ModelDefinitionSummary createModel(
      @PathVariable Long providerId, @RequestBody CreateModelRequest request) {
    return modelProviderAdminService.createModel(
        SecurityUtils.currentUser(),
        providerId,
        new ModelProviderAdminService.CreateModelRequest(
            request.modelCode(),
            request.displayName(),
            request.purpose(),
            request.status(),
            request.isDefault(),
            request.inputPricePer1k(),
            request.outputPricePer1k(),
            request.maxTokens()));
  }

  @PatchMapping("/models/{modelId}/status")
  @PreAuthorize("hasRole('SUPER_ADMIN')")
  public ModelProviderAdminService.ModelDefinitionSummary updateModelStatus(
      @PathVariable Long modelId, @RequestBody UpdateModelStatusRequest request) {
    return modelProviderAdminService.updateModelStatus(
        SecurityUtils.currentUser(), modelId, ModelDefinitionStatus.valueOf(request.status()));
  }

  @PatchMapping("/models/{modelId}/default")
  @PreAuthorize("hasRole('SUPER_ADMIN')")
  public ModelProviderAdminService.ModelDefinitionSummary setDefaultModel(@PathVariable Long modelId) {
    return modelProviderAdminService.setDefaultModel(SecurityUtils.currentUser(), modelId);
  }

  @ExceptionHandler(IllegalArgumentException.class)
  @org.springframework.web.bind.annotation.ResponseStatus(org.springframework.http.HttpStatus.BAD_REQUEST)
  public Map<String, String> handleIllegalArgument(IllegalArgumentException exception) {
    return Map.of("code", exception.getMessage());
  }

  public record CreateProviderRequest(
      @NotBlank String providerCode,
      @NotBlank String displayName,
      String apiEndpoint,
      String apiKey,
      @NotBlank String status,
      String supports,
      @NotBlank String transport,
      String apiKeyEnvVar,
      String apiVersion) {}

  public record UpdateProviderStatusRequest(@NotBlank String status) {}

  public record CreateModelRequest(
      @NotBlank String modelCode,
      @NotBlank String displayName,
      @NotBlank String purpose,
      @NotBlank String status,
      boolean isDefault,
      Double inputPricePer1k,
      Double outputPricePer1k,
      int maxTokens) {}

  public record UpdateModelStatusRequest(@NotBlank String status) {}
}