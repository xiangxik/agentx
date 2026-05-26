import type { ReactElement } from 'react';
import { useEffect, useState } from 'react';

import { ActionButton, AdminShell } from '@agentx/admin-ui';
import type { AuthSession } from '@agentx/api-client';
import { I18nProvider } from '@agentx/i18n';
import { BrowserRouter, Navigate, Route, Routes, useLocation } from 'react-router-dom';

import { ConversationsPage } from './pages/ConversationsPage';
import { DashboardPage } from './pages/DashboardPage';
import { ChatbotsPage } from './pages/ChatbotsPage';
import { KnowledgePage } from './pages/KnowledgePage';
import { LoginPage } from './pages/LoginPage';
import { UsagePage } from './pages/UsagePage';
import { isTenantAdmin, readSession, writeSession } from './session';

function ProtectedApp({ session, onLogout }: { session: AuthSession; onLogout: () => void }) {
  return (
    <AdminShell
      title="Tenant Admin"
      actions={
        <ActionButton onClick={onLogout} tone="quiet" variant="outline" block>
          退出登录
        </ActionButton>
      }
      nav={[
        { to: '/', label: '工作台' },
        { to: '/usage', label: '额度' },
        { to: '/chatbots', label: '机器人' },
        { to: '/knowledge', label: 'FAQ / 知识' },
        { to: '/conversations', label: '会话' }
      ]}
    >
      <Routes>
        <Route path="/" element={<DashboardPage session={session} />} />
        <Route path="/usage" element={<UsagePage session={session} />} />
        <Route path="/chatbots" element={<ChatbotsPage session={session} />} />
        <Route path="/knowledge" element={<KnowledgePage session={session} />} />
        <Route path="/conversations" element={<ConversationsPage session={session} />} />
      </Routes>
    </AdminShell>
  );
}

function ProtectedRoute({ session, children }: { session: AuthSession | null; children: ReactElement }) {
  const location = useLocation();

  if (!isTenantAdmin(session)) {
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

export function TenantAdminApp() {
  return (
    <I18nProvider>
      <BrowserRouter>
        <AppContent />
      </BrowserRouter>
    </I18nProvider>
  );
}
