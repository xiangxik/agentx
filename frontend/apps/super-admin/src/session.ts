import { ApiRequestError, type AuthSession, login } from '@agentx/api-client';

const STORAGE_KEY = 'agentx.super-admin.session';

const errorMessages: Record<string, string> = {
  INVALID_CREDENTIALS: '邮箱或密码错误，请重新输入。',
  ACCOUNT_DISABLED: '当前账号已被禁用，请联系管理员。',
  ACCOUNT_LOCKED: '登录失败次数过多，账号已被临时锁定。',
  TENANT_DISABLED: '当前租户已被停用，暂时无法登录。'
};

export function readSession() {
  if (typeof window === 'undefined') {
    return null;
  }

  const rawValue = window.localStorage.getItem(STORAGE_KEY);

  if (!rawValue) {
    return null;
  }

  try {
    return JSON.parse(rawValue) as AuthSession;
  } catch {
    window.localStorage.removeItem(STORAGE_KEY);
    return null;
  }
}

export function writeSession(session: AuthSession | null) {
  if (typeof window === 'undefined') {
    return;
  }

  if (session) {
    window.localStorage.setItem(STORAGE_KEY, JSON.stringify(session));
    return;
  }

  window.localStorage.removeItem(STORAGE_KEY);
}

export function isSuperAdmin(session: AuthSession | null) {
  return Boolean(session?.roles.includes('SUPER_ADMIN'));
}

export async function submitLogin(email: string, password: string) {
  return login({ email, password });
}

export function resolveLoginError(error: unknown) {
  const message = error instanceof ApiRequestError ? (error.code ?? 'LOGIN_FAILED') : 'LOGIN_FAILED';
  return message === 'FORBIDDEN_ROLE'
    ? '该账号没有平台管理后台访问权限。'
    : (errorMessages[message] ?? '登录失败，请稍后重试。');
}
