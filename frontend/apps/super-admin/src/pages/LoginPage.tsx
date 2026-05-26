import type { FormEvent } from 'react';
import { useEffect, useState } from 'react';

import { Alert, Card, Input, Typography } from 'antd';
import { ActionButton } from '@agentx/admin-ui';
import type { AuthSession } from '@agentx/api-client';
import { useLocation, useNavigate } from 'react-router-dom';

import { isSuperAdmin, resolveLoginError, submitLogin } from '../session';

const { Title, Paragraph, Text } = Typography;

export function LoginPage({
  session,
  onLogin
}: {
  session: AuthSession | null;
  onLogin: (nextSession: AuthSession) => void;
}) {
  const navigate = useNavigate();
  const location = useLocation();
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [submitting, setSubmitting] = useState(false);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);

  useEffect(() => {
    if (isSuperAdmin(session)) {
      const nextPath = (location.state as { from?: { pathname?: string } } | null)?.from?.pathname ?? '/';
      navigate(nextPath, { replace: true });
    }
  }, [location.state, navigate, session]);

  const handleSubmit = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    setSubmitting(true);
    setErrorMessage(null);

    try {
      const nextSession = await submitLogin(email, password);

      if (!isSuperAdmin(nextSession)) {
        throw new Error('FORBIDDEN_ROLE');
      }

      onLogin(nextSession);
    } catch (error) {
      setErrorMessage(resolveLoginError(error));
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <div
      style={{
        minHeight: '100vh',
        display: 'grid',
        placeItems: 'center',
        background: 'linear-gradient(135deg, #eff6ff 0%, #dbeafe 45%, #bfdbfe 100%)',
        padding: 24,
        fontFamily: 'ui-sans-serif, system-ui, sans-serif'
      }}
    >
      <Card style={{ width: 'min(100%, 420px)', borderRadius: 18, boxShadow: '0 30px 80px rgba(30, 64, 175, 0.18)', background: 'rgba(255,255,255,0.95)' }} styles={{ body: { padding: 28 } }}>
        <Text style={{ margin: 0, color: '#1d4ed8', fontWeight: 700, letterSpacing: 1.2 }}>SUPER ADMIN</Text>
        <Title level={2} style={{ marginTop: 12, marginBottom: 8 }}>登录平台管理台</Title>
        <Paragraph style={{ marginTop: 0, marginBottom: 24, color: '#1e3a8a' }}>仅超级管理员账号可进入平台级后台。</Paragraph>
        <form onSubmit={handleSubmit} style={{ display: 'grid', gap: 16 }}>
          <label style={{ display: 'grid', gap: 8 }}>
            <span>邮箱</span>
            <Input
              type="email"
              value={email}
              onChange={(event) => setEmail(event.target.value)}
              required
              placeholder="admin@agentx.local"
              style={{ borderRadius: 14, border: '1px solid #93c5fd', padding: '12px 14px', fontSize: 16 }}
            />
          </label>
          <label style={{ display: 'grid', gap: 8 }}>
            <span>密码</span>
            <Input.Password
              type="password"
              value={password}
              onChange={(event) => setPassword(event.target.value)}
              required
              placeholder="请输入密码"
              style={{ borderRadius: 14, border: '1px solid #93c5fd', padding: '12px 14px', fontSize: 16 }}
            />
          </label>
          {errorMessage ? <Alert type="error" showIcon message={errorMessage} style={{ borderRadius: 10 }} /> : null}
          <ActionButton htmlType="submit" disabled={submitting} block>
            {submitting ? '登录中...' : '登录'}
          </ActionButton>
        </form>
      </Card>
    </div>
  );
}
