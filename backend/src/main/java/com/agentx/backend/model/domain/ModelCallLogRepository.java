package com.agentx.backend.model.domain;

import java.time.Instant;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface ModelCallLogRepository extends JpaRepository<ModelCallLog, Long> {
  List<ModelCallLog> findByConversationIdOrderByIdAsc(Long conversationId);

  @Query(
      """
      select
        count(log) as totalCalls,
        coalesce(sum(case when log.status = com.agentx.backend.model.domain.ModelCallStatus.SUCCESS then 1 else 0 end), 0) as successCalls,
        coalesce(sum(case when log.status = com.agentx.backend.model.domain.ModelCallStatus.FAILED then 1 else 0 end), 0) as failedCalls,
        coalesce(sum(log.totalTokens), 0) as totalTokens,
        coalesce(sum(log.estimatedCost), 0) as totalCost,
        coalesce(avg(log.latencyMs), 0) as avgLatencyMs
      from ModelCallLog log
      where (:tenantId is null or log.tenantId = :tenantId)
        and (:providerCode is null or log.providerCode = :providerCode)
        and (:modelCode is null or log.modelCode = :modelCode)
        and (:createdFrom is null or log.createdAt >= :createdFrom)
        and (:createdTo is null or log.createdAt <= :createdTo)
      """)
  AnalyticsTotalsProjection aggregateTotals(
      @Param("tenantId") Long tenantId,
      @Param("providerCode") String providerCode,
      @Param("modelCode") String modelCode,
      @Param("createdFrom") Instant createdFrom,
      @Param("createdTo") Instant createdTo);

  @Query(
      """
      select
        log.providerCode as providerCode,
        count(log) as totalCalls,
        coalesce(sum(case when log.status = com.agentx.backend.model.domain.ModelCallStatus.FAILED then 1 else 0 end), 0) as failedCalls,
        coalesce(sum(log.totalTokens), 0) as totalTokens,
        coalesce(sum(log.estimatedCost), 0) as totalCost,
        coalesce(avg(log.latencyMs), 0) as avgLatencyMs
      from ModelCallLog log
      where (:tenantId is null or log.tenantId = :tenantId)
        and (:providerCode is null or log.providerCode = :providerCode)
        and (:modelCode is null or log.modelCode = :modelCode)
        and (:createdFrom is null or log.createdAt >= :createdFrom)
        and (:createdTo is null or log.createdAt <= :createdTo)
      group by log.providerCode
      order by count(log) desc, log.providerCode asc
      """)
  List<ProviderAnalyticsProjection> aggregateByProvider(
      @Param("tenantId") Long tenantId,
      @Param("providerCode") String providerCode,
      @Param("modelCode") String modelCode,
      @Param("createdFrom") Instant createdFrom,
      @Param("createdTo") Instant createdTo,
      Pageable pageable);

  @Query(
      """
      select
        log.providerCode as providerCode,
        log.modelCode as modelCode,
        count(log) as totalCalls,
        coalesce(sum(case when log.status = com.agentx.backend.model.domain.ModelCallStatus.FAILED then 1 else 0 end), 0) as failedCalls,
        coalesce(sum(log.totalTokens), 0) as totalTokens,
        coalesce(sum(log.estimatedCost), 0) as totalCost,
        coalesce(avg(log.latencyMs), 0) as avgLatencyMs
      from ModelCallLog log
      where (:tenantId is null or log.tenantId = :tenantId)
        and (:providerCode is null or log.providerCode = :providerCode)
        and (:modelCode is null or log.modelCode = :modelCode)
        and (:createdFrom is null or log.createdAt >= :createdFrom)
        and (:createdTo is null or log.createdAt <= :createdTo)
      group by log.providerCode, log.modelCode
      order by count(log) desc, log.providerCode asc, log.modelCode asc
      """)
  List<ModelAnalyticsProjection> aggregateByModel(
      @Param("tenantId") Long tenantId,
      @Param("providerCode") String providerCode,
      @Param("modelCode") String modelCode,
      @Param("createdFrom") Instant createdFrom,
      @Param("createdTo") Instant createdTo,
      Pageable pageable);

  @Query("select coalesce(sum(log.totalTokens), 0) from ModelCallLog log where log.tenantId = ?1 and log.status = 'SUCCESS'")
  Long sumSuccessfulTokensByTenantId(Long tenantId);

  interface AnalyticsTotalsProjection {
    long getTotalCalls();

    long getSuccessCalls();

    long getFailedCalls();

    long getTotalTokens();

    double getTotalCost();

    double getAvgLatencyMs();
  }

  interface ProviderAnalyticsProjection {
    String getProviderCode();

    long getTotalCalls();

    long getFailedCalls();

    long getTotalTokens();

    double getTotalCost();

    double getAvgLatencyMs();
  }

  interface ModelAnalyticsProjection {
    String getProviderCode();

    String getModelCode();

    long getTotalCalls();

    long getFailedCalls();

    long getTotalTokens();

    double getTotalCost();

    double getAvgLatencyMs();
  }
}