package com.agentx.backend.model.api;

import com.agentx.backend.model.application.ModelAnalyticsAdminService;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/model-analytics")
@PreAuthorize("hasRole('SUPER_ADMIN')")
public class ModelAnalyticsAdminController {

  private final ModelAnalyticsAdminService modelAnalyticsAdminService;

  public ModelAnalyticsAdminController(ModelAnalyticsAdminService modelAnalyticsAdminService) {
    this.modelAnalyticsAdminService = modelAnalyticsAdminService;
  }

  @GetMapping
  public ModelAnalyticsAdminService.AnalyticsOverview getOverview(
      @RequestParam(required = false) Long tenantId,
      @RequestParam(required = false) String providerCode,
      @RequestParam(required = false) String modelCode,
      @RequestParam(required = false) Integer rowLimit,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
          Instant createdFrom,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
          Instant createdTo) {
    return modelAnalyticsAdminService.getOverview(
      buildFilterRequest(tenantId, providerCode, modelCode, createdFrom, createdTo, rowLimit));
    }

    @GetMapping("/export")
    public ResponseEntity<byte[]> export(
      @RequestParam(required = false) Long tenantId,
      @RequestParam(required = false) String providerCode,
      @RequestParam(required = false) String modelCode,
      @RequestParam(required = false) Integer rowLimit,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
        Instant createdFrom,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
        Instant createdTo) {
    ModelAnalyticsAdminService.AnalyticsExport export =
      modelAnalyticsAdminService.exportCsv(
        buildFilterRequest(tenantId, providerCode, modelCode, createdFrom, createdTo, rowLimit));

    return ResponseEntity.ok()
      .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"%s\"".formatted(export.fileName()))
      .contentType(MediaType.parseMediaType("text/csv"))
      .body(export.content().getBytes(StandardCharsets.UTF_8));
    }

    private ModelAnalyticsAdminService.AnalyticsFilterRequest buildFilterRequest(
      Long tenantId,
      String providerCode,
      String modelCode,
      Instant createdFrom,
      Instant createdTo,
      Integer rowLimit) {
    return new ModelAnalyticsAdminService.AnalyticsFilterRequest(
      tenantId, providerCode, modelCode, createdFrom, createdTo, rowLimit);
  }
}