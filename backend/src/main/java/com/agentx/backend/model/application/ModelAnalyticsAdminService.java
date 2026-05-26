package com.agentx.backend.model.application;

import com.agentx.backend.model.domain.ModelCallLogRepository;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ModelAnalyticsAdminService {

    private static final int DEFAULT_ROW_LIMIT = 8;
    private static final int MAX_ROW_LIMIT = 50;
    private static final DateTimeFormatter FILE_TIME_FORMATTER =
            DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'").withZone(ZoneOffset.UTC);

  private final ModelCallLogRepository modelCallLogRepository;

  public ModelAnalyticsAdminService(ModelCallLogRepository modelCallLogRepository) {
    this.modelCallLogRepository = modelCallLogRepository;
  }

  @Transactional(readOnly = true)
    public AnalyticsOverview getOverview(AnalyticsFilterRequest request) {
        int rowLimit = normalizeRowLimit(request.rowLimit());
        ModelCallLogRepository.AnalyticsTotalsProjection totals =
                aggregateTotals(request);
    long totalCalls = totals.getTotalCalls();
    long successCalls = totals.getSuccessCalls();
    long failedCalls = totals.getFailedCalls();
    long totalTokens = totals.getTotalTokens();
    double totalCost = totals.getTotalCost();
    double avgLatencyMs = totals.getAvgLatencyMs();
    double failureRate = totalCalls == 0 ? 0.0 : (failedCalls * 100.0) / totalCalls;
    Window previousWindow = resolvePreviousWindow(request.createdFrom(), request.createdTo());

    List<ModelCallLogRepository.ProviderAnalyticsProjection> currentProviderProjections =
        modelCallLogRepository
            .aggregateByProvider(
                request.tenantId(),
                request.providerCode(),
                request.modelCode(),
                request.createdFrom(),
                request.createdTo(),
                PageRequest.of(0, rowLimit));

    List<ModelCallLogRepository.ModelAnalyticsProjection> currentModelProjections =
        modelCallLogRepository
            .aggregateByModel(
                request.tenantId(),
                request.providerCode(),
                request.modelCode(),
                request.createdFrom(),
                request.createdTo(),
                PageRequest.of(0, rowLimit));

    Map<String, ModelCallLogRepository.ProviderAnalyticsProjection> previousProviderByCode =
        previousWindow == null
            ? Map.of()
            : indexProviders(
                modelCallLogRepository.aggregateByProvider(
                    request.tenantId(),
                    request.providerCode(),
                    request.modelCode(),
                    previousWindow.createdFrom(),
                    previousWindow.createdTo(),
                    PageRequest.of(0, rowLimit)));

    Map<String, ModelCallLogRepository.ModelAnalyticsProjection> previousModelByKey =
        previousWindow == null
            ? Map.of()
            : indexModels(
                modelCallLogRepository.aggregateByModel(
                    request.tenantId(),
                    request.providerCode(),
                    request.modelCode(),
                    previousWindow.createdFrom(),
                    previousWindow.createdTo(),
                    PageRequest.of(0, rowLimit)));

    List<ProviderAnalytics> providers =
        currentProviderProjections.stream()
            .map(projection -> toProviderAnalytics(projection, previousProviderByCode.get(projection.getProviderCode())))
            .toList();

    List<ModelAnalytics> models =
        currentModelProjections.stream()
            .map(projection -> toModelAnalytics(projection, previousModelByKey.get(modelKey(projection))))
            .toList();

    TrendSummary trends = buildTrendSummary(request, totals, previousWindow);

    return new AnalyticsOverview(
        totalCalls,
        successCalls,
        failedCalls,
        failureRate,
        totalTokens,
        totalCost,
        avgLatencyMs,
                trends,
        providers,
        models);
  }

    @Transactional(readOnly = true)
    public AnalyticsExport exportCsv(AnalyticsFilterRequest request) {
        AnalyticsOverview overview = getOverview(request);
        StringBuilder content = new StringBuilder();
        content.append("section,metric,current_value,previous_value,delta_value,delta_percent\n");
        appendTrendRow(content, "summary", "totalCalls", overview.totalCalls(), overview.trends());
        appendTrendRow(content, "summary", "totalTokens", overview.totalTokens(), overview.trends());
        appendTrendRow(content, "summary", "totalCost", overview.totalCost(), overview.trends());
        appendTrendRow(content, "summary", "failureRate", overview.failureRate(), overview.trends());
        content.append('\n');
        content.append("section,provider_code,total_calls,failed_calls,failure_rate,total_tokens,total_cost,avg_latency_ms,previous_total_calls,delta_total_calls,delta_total_calls_percent\n");
        overview.providers().forEach(provider ->
                content
                        .append("provider,")
                        .append(csv(provider.providerCode())).append(',')
                        .append(provider.totalCalls()).append(',')
                        .append(provider.failedCalls()).append(',')
                        .append(provider.failureRate()).append(',')
                        .append(provider.totalTokens()).append(',')
                        .append(provider.totalCost()).append(',')
                            .append(provider.avgLatencyMs()).append(',')
                            .append(provider.trends() == null ? "" : provider.trends().totalCalls().previousValue()).append(',')
                            .append(provider.trends() == null ? "" : provider.trends().totalCalls().deltaValue()).append(',')
                            .append(provider.trends() == null || provider.trends().totalCalls().deltaPercent() == null ? "" : provider.trends().totalCalls().deltaPercent())
                        .append('\n'));
        content.append('\n');
                        content.append("section,provider_code,model_code,total_calls,failed_calls,failure_rate,total_tokens,total_cost,avg_latency_ms,previous_total_calls,delta_total_calls,delta_total_calls_percent\n");
        overview.models().forEach(model ->
                content
                        .append("model,")
                        .append(csv(model.providerCode())).append(',')
                        .append(csv(model.modelCode())).append(',')
                        .append(model.totalCalls()).append(',')
                        .append(model.failedCalls()).append(',')
                        .append(model.failureRate()).append(',')
                        .append(model.totalTokens()).append(',')
                        .append(model.totalCost()).append(',')
                        .append(model.avgLatencyMs()).append(',')
                        .append(model.trends() == null ? "" : model.trends().totalCalls().previousValue()).append(',')
                        .append(model.trends() == null ? "" : model.trends().totalCalls().deltaValue()).append(',')
                        .append(model.trends() == null || model.trends().totalCalls().deltaPercent() == null ? "" : model.trends().totalCalls().deltaPercent())
                        .append('\n'));

        return new AnalyticsExport(
                    buildExportFileName(request),
                content.toString());
    }

    private ModelCallLogRepository.AnalyticsTotalsProjection aggregateTotals(AnalyticsFilterRequest request) {
        return modelCallLogRepository.aggregateTotals(
                request.tenantId(),
                request.providerCode(),
                request.modelCode(),
                request.createdFrom(),
                request.createdTo());
    }

    private TrendSummary buildTrendSummary(
            AnalyticsFilterRequest request,
            ModelCallLogRepository.AnalyticsTotalsProjection currentTotals,
            Window previousWindow) {
        if (previousWindow == null) {
            return null;
        }

        ModelCallLogRepository.AnalyticsTotalsProjection previousTotals =
                modelCallLogRepository.aggregateTotals(
                        request.tenantId(),
                        request.providerCode(),
                        request.modelCode(),
                        previousWindow.createdFrom(),
                        previousWindow.createdTo());
        double currentFailureRate =
                currentTotals.getTotalCalls() == 0
                        ? 0.0
                        : (currentTotals.getFailedCalls() * 100.0) / currentTotals.getTotalCalls();
        double previousFailureRate =
                previousTotals.getTotalCalls() == 0
                        ? 0.0
                        : (previousTotals.getFailedCalls() * 100.0) / previousTotals.getTotalCalls();

        return new TrendSummary(
                new TrendMetric(
                        currentTotals.getTotalCalls(),
                        previousTotals.getTotalCalls(),
                        currentTotals.getTotalCalls() - previousTotals.getTotalCalls(),
                        percentDelta(currentTotals.getTotalCalls(), previousTotals.getTotalCalls())),
                new TrendMetric(
                        currentTotals.getTotalTokens(),
                        previousTotals.getTotalTokens(),
                        currentTotals.getTotalTokens() - previousTotals.getTotalTokens(),
                        percentDelta(currentTotals.getTotalTokens(), previousTotals.getTotalTokens())),
                new TrendMetric(
                        currentTotals.getTotalCost(),
                        previousTotals.getTotalCost(),
                        currentTotals.getTotalCost() - previousTotals.getTotalCost(),
                        percentDelta(currentTotals.getTotalCost(), previousTotals.getTotalCost())),
                new TrendMetric(
                        currentFailureRate,
                        previousFailureRate,
                        currentFailureRate - previousFailureRate,
                        percentDelta(currentFailureRate, previousFailureRate)));
    }

    private Map<String, ModelCallLogRepository.ProviderAnalyticsProjection> indexProviders(
            List<ModelCallLogRepository.ProviderAnalyticsProjection> projections) {
        Map<String, ModelCallLogRepository.ProviderAnalyticsProjection> indexed = new HashMap<>();
        projections.forEach(projection -> indexed.put(projection.getProviderCode(), projection));
        return indexed;
    }

    private Map<String, ModelCallLogRepository.ModelAnalyticsProjection> indexModels(
            List<ModelCallLogRepository.ModelAnalyticsProjection> projections) {
        Map<String, ModelCallLogRepository.ModelAnalyticsProjection> indexed = new HashMap<>();
        projections.forEach(projection -> indexed.put(modelKey(projection), projection));
        return indexed;
    }

    private String modelKey(ModelCallLogRepository.ModelAnalyticsProjection projection) {
        return projection.getProviderCode() + "::" + projection.getModelCode();
    }

    private String buildExportFileName(AnalyticsFilterRequest request) {
        StringBuilder fileName = new StringBuilder("model-analytics");
        if (request.tenantId() != null) {
            fileName.append("-tenant-").append(request.tenantId());
        }
        if (request.providerCode() != null && !request.providerCode().isBlank()) {
            fileName.append("-provider-").append(sanitizeFileToken(request.providerCode()));
        }
        if (request.modelCode() != null && !request.modelCode().isBlank()) {
            fileName.append("-model-").append(sanitizeFileToken(request.modelCode()));
        }
        if (request.createdFrom() != null && request.createdTo() != null) {
            fileName
                    .append('-')
                    .append(FILE_TIME_FORMATTER.format(request.createdFrom()))
                    .append("_to_")
                    .append(FILE_TIME_FORMATTER.format(request.createdTo()));
        } else if (request.createdFrom() != null) {
            fileName.append("-from-").append(FILE_TIME_FORMATTER.format(request.createdFrom()));
        } else if (request.createdTo() != null) {
            fileName.append("-to-").append(FILE_TIME_FORMATTER.format(request.createdTo()));
        }
        fileName.append("-top-").append(normalizeRowLimit(request.rowLimit()));
        fileName.append(".csv");
        return fileName.toString();
    }

    private String sanitizeFileToken(String value) {
        return value.trim().replaceAll("[^a-zA-Z0-9._-]+", "-");
    }

    private Window resolvePreviousWindow(Instant createdFrom, Instant createdTo) {
        if (createdFrom == null || createdTo == null || !createdTo.isAfter(createdFrom)) {
            return null;
        }
        long durationMillis = Duration.between(createdFrom, createdTo).toMillis();
        if (durationMillis <= 0) {
            return null;
        }
        Instant previousTo = createdFrom.minusMillis(1);
        Instant previousFrom = previousTo.minusMillis(durationMillis);
        return new Window(previousFrom, previousTo);
    }

    private Double percentDelta(double currentValue, double previousValue) {
        if (previousValue == 0.0) {
            return null;
        }
        return ((currentValue - previousValue) / previousValue) * 100.0;
    }

    private void appendTrendRow(
            StringBuilder content,
            String section,
            String metric,
            Number currentValue,
            TrendSummary trends) {
        TrendMetric trendMetric =
                trends == null
                        ? null
                        : switch (metric) {
                            case "totalCalls" -> trends.totalCalls();
                            case "totalTokens" -> trends.totalTokens();
                            case "totalCost" -> trends.totalCost();
                            case "failureRate" -> trends.failureRate();
                            default -> null;
                        };
        content
                .append(section).append(',')
                .append(metric).append(',')
                .append(currentValue);
        if (trendMetric == null) {
            content.append(",,,\n");
            return;
        }
        content
                .append(',').append(trendMetric.previousValue())
                .append(',').append(trendMetric.deltaValue())
                .append(',').append(trendMetric.deltaPercent() == null ? "" : trendMetric.deltaPercent())
                .append('\n');
    }

    private String csv(String value) {
        if (value == null) {
            return "";
        }
        return '"' + value.replace("\"", "\"\"") + '"';
    }

    private ProviderAnalytics toProviderAnalytics(
            ModelCallLogRepository.ProviderAnalyticsProjection projection,
            ModelCallLogRepository.ProviderAnalyticsProjection previousProjection) {
        long totalCalls = projection.getTotalCalls();
        long failedCalls = projection.getFailedCalls();
        long totalTokens = projection.getTotalTokens();
        double totalCost = projection.getTotalCost();
        double avgLatencyMs = projection.getAvgLatencyMs();
        double failureRate = totalCalls == 0 ? 0.0 : (failedCalls * 100.0) / totalCalls;
        Trends trends = buildRankingTrends(totalCalls, failedCalls, totalTokens, totalCost, previousProjection);
    return new ProviderAnalytics(
                projection.getProviderCode(),
                totalCalls,
                failedCalls,
                failureRate,
                totalTokens,
                totalCost,
                avgLatencyMs,
                trends);
  }

    private ModelAnalytics toModelAnalytics(
            ModelCallLogRepository.ModelAnalyticsProjection projection,
            ModelCallLogRepository.ModelAnalyticsProjection previousProjection) {
        long totalCalls = projection.getTotalCalls();
        long failedCalls = projection.getFailedCalls();
        long totalTokens = projection.getTotalTokens();
        double totalCost = projection.getTotalCost();
        double avgLatencyMs = projection.getAvgLatencyMs();
        double failureRate = totalCalls == 0 ? 0.0 : (failedCalls * 100.0) / totalCalls;
        Trends trends = buildRankingTrends(totalCalls, failedCalls, totalTokens, totalCost, previousProjection);
    return new ModelAnalytics(
                projection.getProviderCode(),
                projection.getModelCode(),
                totalCalls,
                failedCalls,
                failureRate,
                totalTokens,
                totalCost,
                avgLatencyMs,
                trends);
  }

    private Trends buildRankingTrends(
            long totalCalls,
            long failedCalls,
            long totalTokens,
            double totalCost,
            Object previousProjection) {
        long previousTotalCalls;
        long previousFailedCalls;
        long previousTotalTokens;
        double previousTotalCost;
        if (previousProjection == null) {
            previousTotalCalls = 0;
            previousFailedCalls = 0;
            previousTotalTokens = 0;
            previousTotalCost = 0.0;
        } else if (previousProjection instanceof ModelCallLogRepository.ProviderAnalyticsProjection providerProjection) {
            previousTotalCalls = providerProjection.getTotalCalls();
            previousFailedCalls = providerProjection.getFailedCalls();
            previousTotalTokens = providerProjection.getTotalTokens();
            previousTotalCost = providerProjection.getTotalCost();
        } else {
            ModelCallLogRepository.ModelAnalyticsProjection modelProjection =
                    (ModelCallLogRepository.ModelAnalyticsProjection) previousProjection;
            previousTotalCalls = modelProjection.getTotalCalls();
            previousFailedCalls = modelProjection.getFailedCalls();
            previousTotalTokens = modelProjection.getTotalTokens();
            previousTotalCost = modelProjection.getTotalCost();
        }

        double failureRate = totalCalls == 0 ? 0.0 : (failedCalls * 100.0) / totalCalls;
        double previousFailureRate =
                previousTotalCalls == 0 ? 0.0 : (previousFailedCalls * 100.0) / previousTotalCalls;

        return new Trends(
                new TrendMetric(totalCalls, previousTotalCalls, totalCalls - previousTotalCalls, percentDelta(totalCalls, previousTotalCalls)),
                new TrendMetric(totalTokens, previousTotalTokens, totalTokens - previousTotalTokens, percentDelta(totalTokens, previousTotalTokens)),
                new TrendMetric(totalCost, previousTotalCost, totalCost - previousTotalCost, percentDelta(totalCost, previousTotalCost)),
                new TrendMetric(failureRate, previousFailureRate, failureRate - previousFailureRate, percentDelta(failureRate, previousFailureRate)));
    }

    private int normalizeRowLimit(Integer rowLimit) {
        if (rowLimit == null || rowLimit <= 0) {
            return DEFAULT_ROW_LIMIT;
        }
        return Math.min(rowLimit, MAX_ROW_LIMIT);
    }

  public record AnalyticsOverview(
      long totalCalls,
      long successCalls,
      long failedCalls,
      double failureRate,
      long totalTokens,
      double totalCost,
      double avgLatencyMs,
      TrendSummary trends,
      List<ProviderAnalytics> providers,
      List<ModelAnalytics> models) {}

  public record TrendSummary(
      TrendMetric totalCalls,
      TrendMetric totalTokens,
      TrendMetric totalCost,
      TrendMetric failureRate) {}

  public record TrendMetric(
      double currentValue,
      double previousValue,
      double deltaValue,
      Double deltaPercent) {}

  public record ProviderAnalytics(
      String providerCode,
      long totalCalls,
      long failedCalls,
      double failureRate,
      long totalTokens,
      double totalCost,
      double avgLatencyMs,
      Trends trends) {}

  public record ModelAnalytics(
      String providerCode,
      String modelCode,
      long totalCalls,
      long failedCalls,
      double failureRate,
      long totalTokens,
      double totalCost,
      double avgLatencyMs,
      Trends trends) {}

  public record Trends(
      TrendMetric totalCalls,
      TrendMetric totalTokens,
      TrendMetric totalCost,
      TrendMetric failureRate) {}

    public record AnalyticsFilterRequest(
            Long tenantId,
            String providerCode,
            String modelCode,
            Instant createdFrom,
          Instant createdTo,
          Integer rowLimit) {}

      public record AnalyticsExport(String fileName, String content) {}

      private record Window(Instant createdFrom, Instant createdTo) {}
}