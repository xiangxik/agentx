import type { FormEvent } from 'react';
import { useEffect, useState } from 'react';

import { Alert, Card, Input, Typography } from 'antd';
import type { AuthSession } from '@agentx/api-client';
import { useLocation, useNavigate } from 'react-router-dom';

import { ActionButton } from '@agentx/admin-ui';
import { Field, inputStyle } from '../form-ui';
import { isTenantAdmin, resolveLoginError, submitLogin } from '../session';

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
    if (isTenantAdmin(session)) {
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

      if (!isTenantAdmin(nextSession)) {
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
        background: 'linear-gradient(135deg, #fff7ed 0%, #ffedd5 45%, #fde68a 100%)',
        padding: 24,
        fontFamily: 'ui-sans-serif, system-ui, sans-serif'
      }}
    >
      <Card style={{ width: 'min(100%, 420px)', borderRadius: 18, boxShadow: '0 30px 80px rgba(120, 53, 15, 0.18)', background: 'rgba(255,255,255,0.94)' }} styles={{ body: { padding: 28 } }}>
        <Text style={{ color: '#9a3412', fontWeight: 700, letterSpacing: 1.2 }}>租户管理员</Text>
        <Title level={2} style={{ marginTop: 12, marginBottom: 8 }}>登录租户管理台</Title>
        <Paragraph style={{ marginTop: 0, marginBottom: 24, color: '#7c2d12' }}>使用租户管理员邮箱和密码进入后台。</Paragraph>
        <form onSubmit={handleSubmit} style={{ display: 'grid', gap: 16 }}>
          <Field label="邮箱">
            <Input
              type="email"
              value={email}
              onChange={(event) => setEmail(event.target.value)}
              required
              placeholder="admin@tenant.local"
              style={{ ...inputStyle(), fontSize: 16 }}
            />
          </Field>
          <Field label="密码">
            <Input.Password
              type="password"
              value={password}
              onChange={(event) => setPassword(event.target.value)}
              required
              placeholder="请输入密码"
              style={{ ...inputStyle(), fontSize: 16 }}
            />
          </Field>
          {errorMessage ? <Alert type="error" showIcon message={errorMessage} style={{ borderRadius: 10 }} /> : null}
          <ActionButton htmlType="submit" disabled={submitting} tone="warning" block>
            {submitting ? '登录中...' : '登录'}
          </ActionButton>
        </form>
      </Card>
    </div>
  );
}
