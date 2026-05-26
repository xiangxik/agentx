import { useEffect, useState } from 'react';

import { PageStack, SectionHeader, SurfaceCard } from '@agentx/admin-ui';
import { getTenantQuotaOverview, type AuthSession, type TenantQuotaOverview } from '@agentx/api-client';

import { Banner } from '../form-ui';

export function UsagePage({ session }: { session: AuthSession }) {
  const [overview, setOverview] = useState<TenantQuotaOverview | null>(null);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);

  useEffect(() => {
    void getTenantQuotaOverview(session.accessToken)
      .then((nextOverview) => {
        setOverview(nextOverview);
        setErrorMessage(null);
      })
      .catch((error) => {
        setOverview(null);
        setErrorMessage(error instanceof Error ? error.message : '额度概览加载失败。');
      });
  }, [session.accessToken]);

  return (
    <>
      <SectionHeader title="额度与用量" />
      {errorMessage ? <Banner tone="error">{errorMessage}</Banner> : null}
      {!overview ? (
        <p style={{ color: '#64748b' }}>当前未分配套餐，或额度数据仍在加载。</p>
      ) : (
        <PageStack gap={20}>
          <div style={{ display: 'grid', gridTemplateColumns: 'minmax(320px, 1fr) minmax(320px, 1fr)', gap: 20 }}>
            <SurfaceCard title="套餐信息" description="查看当前租户生效的套餐编码和状态。">
              <div>套餐：{overview.planName}</div>
              <div>编码：{overview.planCode}</div>
              <div>状态：{overview.planStatus}</div>
            </SurfaceCard>
            <SurfaceCard title="资源用量" description="按当前套餐额度查看各项资源消耗。">
              {Object.entries(overview.effectiveLimits).map(([key, limit]) => (
                <div key={key} style={{ display: 'flex', justifyContent: 'space-between', gap: 12 }}>
                  <span>{key}</span>
                  <span>
                    {overview.usage[key] ?? 0} / {limit}
                  </span>
                </div>
              ))}
            </SurfaceCard>
          </div>
        </PageStack>
      )}
    </>
  );
}
