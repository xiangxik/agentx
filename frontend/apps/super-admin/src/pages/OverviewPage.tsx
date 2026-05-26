import { useEffect, useState } from 'react';

import { SectionHeader, StatCard } from '@agentx/admin-ui';
import { listAuditLogs, listPlans, listTenants, type AuthSession } from '@agentx/api-client';

export function OverviewPage({ session }: { session: AuthSession }) {
  const token = session.accessToken;
  const [tenantCount, setTenantCount] = useState<number | null>(null);
  const [planCount, setPlanCount] = useState<number | null>(null);
  const [auditCount, setAuditCount] = useState<number | null>(null);
  const [highRiskCount, setHighRiskCount] = useState<number | null>(null);

  useEffect(() => {
    let cancelled = false;

    const loadOverview = async () => {
      try {
        const [tenants, plans, audits, highRiskAudits] = await Promise.all([
          listTenants(token),
          listPlans(token),
          listAuditLogs(token, {}),
          listAuditLogs(token, { riskLevel: 'HIGH' })
        ]);

        if (cancelled) {
          return;
        }

        setTenantCount(tenants.length);
        setPlanCount(plans.length);
        setAuditCount(audits.length);
        setHighRiskCount(highRiskAudits.length);
      } catch {
        if (!cancelled) {
          setTenantCount(null);
          setPlanCount(null);
          setAuditCount(null);
          setHighRiskCount(null);
        }
      }
    };

    void loadOverview();

    return () => {
      cancelled = true;
    };
  }, [token]);

  return (
    <>
      <SectionHeader title="平台监控" />
      <div style={{ display: 'grid', gridTemplateColumns: 'repeat(4, minmax(0, 1fr))', gap: 16 }}>
        <StatCard title="租户数" value={tenantCount == null ? '-' : String(tenantCount)} description="来自当前平台租户列表。" />
        <StatCard title="套餐数" value={planCount == null ? '-' : String(planCount)} description="包含启用和停用中的套餐。" />
        <StatCard title="审计总量" value={auditCount == null ? '-' : String(auditCount)} description="来自平台审计日志。" />
        <StatCard title="高风险审计" value={highRiskCount == null ? '-' : String(highRiskCount)} description="基于 riskLevel=HIGH 统计。" />
      </div>
    </>
  );
}
