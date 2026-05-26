import { useEffect, useState } from 'react';

import { SectionHeader, StatCard } from '@agentx/admin-ui';
import { getTenantQuotaOverview, type AuthSession, type TenantQuotaOverview } from '@agentx/api-client';
import { useI18n } from '@agentx/i18n';

export function DashboardPage({ session }: { session: AuthSession }) {
  const { t } = useI18n();
  const [quotaOverview, setQuotaOverview] = useState<TenantQuotaOverview | null>(null);

  useEffect(() => {
    void getTenantQuotaOverview(session.accessToken)
      .then((overview) => setQuotaOverview(overview))
      .catch(() => setQuotaOverview(null));
  }, [session.accessToken]);

  const chatbotUsed = quotaOverview?.usage.chatbots ?? 0;
  const chatbotLimit = quotaOverview?.effectiveLimits.chatbots ?? 0;
  const messageUsed = quotaOverview?.usage.messages ?? 0;
  const messageLimit = quotaOverview?.effectiveLimits.messages ?? 0;
  const conversationUsed = quotaOverview?.usage.conversations ?? 0;

  return (
    <>
      <SectionHeader title={t('dashboard')} />
      <div style={{ display: 'grid', gridTemplateColumns: 'repeat(3, minmax(0, 1fr))', gap: 16 }}>
        <StatCard title="机器人数量" value={`${chatbotUsed} / ${chatbotLimit || '-'}`} description={quotaOverview ? `套餐 ${quotaOverview.planName}，状态 ${quotaOverview.planStatus}` : '额度概览加载中或暂未配置套餐。'} />
        <StatCard title="消息用量" value={`${messageUsed} / ${messageLimit || '-'}`} description="消息数来自已落库对话消息。" />
        <StatCard title="会话总数" value={String(conversationUsed)} description="可在会话页继续查看详情、导出与删除。" />
      </div>
    </>
  );
}
