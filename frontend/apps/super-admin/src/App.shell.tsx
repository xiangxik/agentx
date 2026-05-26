import type { ReactElement } from 'react';
import { useEffect, useState } from 'react';

import { ActionButton, AdminShell } from '@agentx/admin-ui';
import type { AuthSession } from '@agentx/api-client';
import { I18nProvider } from '@agentx/i18n';
import { BrowserRouter, Navigate, Route, Routes, useLocation } from 'react-router-dom';

import { AuditLogsPage } from './pages/AuditLogsPage';
import { LoginPage } from './pages/LoginPage';
import { ModelsPage } from './pages/ModelsPage';
import { OverviewPage } from './pages/OverviewPage';
import { PlansPage } from './pages/PlansPage';
import { TenantsPage } from './pages/TenantsPage';
import { isSuperAdmin, readSession, writeSession } from './session';

function ProtectedApp({ session, onLogout }: { session: AuthSession; onLogout: () => void }) {
  return (
    <AdminShell
      title="Super Admin"
      actions={
        <ActionButton onClick={onLogout} tone="quiet" variant="outline" block>
          退出登录
        </ActionButton>
      }
      nav={[
        { to: '/', label: '平台概览' },
        { to: '/tenants', label: '租户' },
        { to: '/plans', label: '套餐' },
        { to: '/models', label: '模型' },
        { to: '/audit', label: '审计' }
      ]}
    >
      <Routes>
        <Route path="/" element={<OverviewPage session={session} />} />
        <Route path="/tenants" element={<TenantsPage session={session} />} />
        <Route path="/plans" element={<PlansPage session={session} />} />
        <Route path="/models" element={<ModelsPage session={session} />} />
        <Route path="/audit" element={<AuditLogsPage session={session} />} />
      </Routes>
    </AdminShell>
  );
}

function ProtectedRoute({ session, children }: { session: AuthSession | null; children: ReactElement }) {
  const location = useLocation();

  if (!isSuperAdmin(session)) {
    return <Navigate to="/login" replace state={{ from: location }} />;
  }

  return children;
}

function AppContent() {
  const [session, setSession] = useState<AuthSession | null>(() => readSession());

  useEffect(() => {
    writeSession(session);
  }, [session]);

  return (
    <Routes>
      <Route path="/login" element={<LoginPage session={session} onLogin={setSession} />} />
      <Route
        path="/*"
        element={
          <ProtectedRoute session={session}>
            <ProtectedApp session={session as AuthSession} onLogout={() => setSession(null)} />
          </ProtectedRoute>
        }
      />
    </Routes>
  );
}

export function SuperAdminApp() {
  return (
    <I18nProvider>
      <BrowserRouter>
        <AppContent />
      </BrowserRouter>
    </I18nProvider>
  );
}
