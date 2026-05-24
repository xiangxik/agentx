import { AdminShell, SectionHeader, StatCard } from '@agentx/admin-ui';
import { I18nProvider, useI18n } from '@agentx/i18n';
import { BrowserRouter, Route, Routes } from 'react-router-dom';

function Dashboard() {
  const { t } = useI18n();

  return (
    <>
      <SectionHeader title={t('dashboard')} />
      <div style={{ display: 'grid', gridTemplateColumns: 'repeat(3, minmax(0, 1fr))', gap: 16 }}>
        <StatCard title="Chatbot 数量" value="0 / 3" description="套餐维度校验将在后端接入。" />
        <StatCard title="消息量" value="0 / 1000" description="用量回写与告警待实现。" />
        <StatCard title="待跟进" value="0" description="MVP 将在对话闭环中接入。" />
      </div>
    </>
  );
}

function Chatbots() {
  return (
    <>
      <SectionHeader title="Chatbot 管理" />
      <p>这里将承接 chatbot 基础信息、外观配置、行为策略与部署联动。</p>
    </>
  );
}

function AppContent() {
  return (
    <AdminShell
      title="Tenant Admin"
      nav={[
        { to: '/', label: '工作台' },
        { to: '/chatbots', label: 'Chatbot' },
        { to: '/knowledge', label: '知识库' },
        { to: '/conversations', label: '会话' }
      ]}
    >
      <Routes>
        <Route path="/" element={<Dashboard />} />
        <Route path="/chatbots" element={<Chatbots />} />
        <Route path="/knowledge" element={<p>知识库骨架页面</p>} />
        <Route path="/conversations" element={<p>会话治理骨架页面</p>} />
      </Routes>
    </AdminShell>
  );
}

export function App() {
  return (
    <I18nProvider>
      <BrowserRouter>
        <AppContent />
      </BrowserRouter>
    </I18nProvider>
  );
}
