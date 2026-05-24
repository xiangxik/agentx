import { AdminShell, SectionHeader, StatCard } from '@agentx/admin-ui';
import { I18nProvider } from '@agentx/i18n';
import { BrowserRouter, Route, Routes } from 'react-router-dom';

function Overview() {
  return (
    <>
      <SectionHeader title="平台监控" />
      <div style={{ display: 'grid', gridTemplateColumns: 'repeat(4, minmax(0, 1fr))', gap: 16 }}>
        <StatCard title="租户数" value="0" description="租户管理模块完成后接入。" />
        <StatCard title="平台消息量" value="0" description="来源于会话与用量模块。" />
        <StatCard title="模型成本" value="¥0" description="来源于模型调用日志。" />
        <StatCard title="高风险审计" value="0" description="审计日志模块完成后接入。" />
      </div>
    </>
  );
}

function AppContent() {
  return (
    <AdminShell
      title="Super Admin"
      nav={[
        { to: '/', label: '平台概览' },
        { to: '/tenants', label: '租户' },
        { to: '/plans', label: '套餐' },
        { to: '/audit', label: '审计' }
      ]}
    >
      <Routes>
        <Route path="/" element={<Overview />} />
        <Route path="/tenants" element={<p>租户管理骨架页面</p>} />
        <Route path="/plans" element={<p>套餐与额度骨架页面</p>} />
        <Route path="/audit" element={<p>审计日志骨架页面</p>} />
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
