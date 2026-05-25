import type { FormEvent, ReactElement } from 'react';
import { useEffect, useState } from 'react';

import { AdminShell, SectionHeader, StatCard } from '@agentx/admin-ui';
import {
  ApiRequestError,
  type AuthSession,
  assignTenantPlan,
  createTenant,
  createPlan,
  getAuditLog,
  getTenantPlanAssignment,
  getTenant,
  listAuditLogs,
  listPlans,
  login,
  listTenants,
  type AuditLogDetail,
  type AuditLogSummary,
  type CreateTenantRequest,
  type CreatePlanRequest,
  type PlanSummary,
  type TenantDetail,
  type TenantSummary,
  updatePlan,
  updatePlanStatus,
  updateTenant,
  updateTenantStatus,
  type UpdatePlanRequest,
  type UpdateTenantRequest
} from '@agentx/api-client';
import { I18nProvider } from '@agentx/i18n';
import { BrowserRouter, Navigate, Route, Routes, useLocation, useNavigate } from 'react-router-dom';

const STORAGE_KEY = 'agentx.super-admin.session';

const errorMessages: Record<string, string> = {
  INVALID_CREDENTIALS: '邮箱或密码错误，请重新输入。',
  ACCOUNT_DISABLED: '当前账号已被禁用，请联系管理员。',
  ACCOUNT_LOCKED: '登录失败次数过多，账号已被临时锁定。',
  TENANT_DISABLED: '当前租户已被停用，暂时无法登录。'
};

function readSession() {
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

function writeSession(session: AuthSession | null) {
  if (typeof window === 'undefined') {
    return;
  }

  if (session) {
    window.localStorage.setItem(STORAGE_KEY, JSON.stringify(session));
    return;
  }

  window.localStorage.removeItem(STORAGE_KEY);
}

function isSuperAdmin(session: AuthSession | null) {
  return Boolean(session?.roles.includes('SUPER_ADMIN'));
}

async function submitLogin(email: string, password: string) {
  return login({ email, password });
}

function LoginPage({
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
      const message = error instanceof ApiRequestError ? (error.code ?? 'LOGIN_FAILED') : 'LOGIN_FAILED';
      setErrorMessage(
        message === 'FORBIDDEN_ROLE'
          ? '该账号没有平台管理后台访问权限。'
          : (errorMessages[message] ?? '登录失败，请稍后重试。')
      );
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
      <div
        style={{
          width: 'min(100%, 420px)',
          background: 'rgba(255,255,255,0.95)',
          borderRadius: 24,
          padding: 28,
          boxShadow: '0 30px 80px rgba(30, 64, 175, 0.18)'
        }}
      >
        <p style={{ margin: 0, color: '#1d4ed8', fontWeight: 700, letterSpacing: 1.2 }}>SUPER ADMIN</p>
        <h1 style={{ marginTop: 12, marginBottom: 8 }}>登录平台管理台</h1>
        <p style={{ marginTop: 0, marginBottom: 24, color: '#1e3a8a' }}>仅超级管理员账号可进入平台级后台。</p>
        <form onSubmit={handleSubmit} style={{ display: 'grid', gap: 16 }}>
          <label style={{ display: 'grid', gap: 8 }}>
            <span>邮箱</span>
            <input
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
            <input
              type="password"
              value={password}
              onChange={(event) => setPassword(event.target.value)}
              required
              placeholder="请输入密码"
              style={{ borderRadius: 14, border: '1px solid #93c5fd', padding: '12px 14px', fontSize: 16 }}
            />
          </label>
          {errorMessage ? (
            <div
              role="alert"
              style={{ borderRadius: 14, background: '#eff6ff', color: '#1d4ed8', padding: '12px 14px', fontSize: 14 }}
            >
              {errorMessage}
            </div>
          ) : null}
          <button
            type="submit"
            disabled={submitting}
            style={{
              border: 0,
              borderRadius: 999,
              padding: '14px 18px',
              background: '#1d4ed8',
              color: '#fff',
              fontSize: 16,
              fontWeight: 700,
              cursor: submitting ? 'progress' : 'pointer',
              opacity: submitting ? 0.8 : 1
            }}
          >
            {submitting ? '登录中...' : '登录'}
          </button>
        </form>
      </div>
    </div>
  );
}

function Overview({ session }: { session: AuthSession }) {
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

type TenantCreateFormState = CreateTenantRequest;
type TenantUpdateFormState = UpdateTenantRequest;
type PlanCreateFormState = {
  code: string;
  name: string;
  limits: Record<string, string>;
};
type PlanUpdateFormState = {
  name: string;
  limits: Record<string, string>;
};

const quotaFields = [
  { key: 'chatbots', label: 'Chatbot 数量' },
  { key: 'messages', label: '消息数' },
  { key: 'tokens', label: 'Token 数' },
  { key: 'files', label: '文件数' },
  { key: 'storageMb', label: '存储(MB)' }
] as const;

const emptyCreateForm = (): TenantCreateFormState => ({
  code: '',
  name: '',
  contactName: '',
  contactEmail: '',
  notes: '',
  adminEmail: '',
  adminDisplayName: '',
  adminPassword: ''
});

const emptyUpdateForm = (): TenantUpdateFormState => ({
  name: '',
  contactName: '',
  contactEmail: '',
  notes: ''
});

function normalizeFormValue(value: string) {
  return value.trim();
}

function normalizeOptionalValue(value: string) {
  const normalized = normalizeFormValue(value);
  return normalized;
}

function statusLabel(status: TenantSummary['status']) {
  return status === 'ACTIVE' ? '启用中' : '已停用';
}

function statusColor(status: TenantSummary['status']) {
  return status === 'ACTIVE' ? '#166534' : '#991b1b';
}

function statusBackground(status: TenantSummary['status']) {
  return status === 'ACTIVE' ? '#dcfce7' : '#fee2e2';
}

function buildLimitFormState(limits?: Record<string, number>) {
  return Object.fromEntries(
    quotaFields.map((field) => [field.key, limits?.[field.key] == null ? '' : String(limits[field.key])])
  );
}

function parseLimitFormState(limits: Record<string, string>) {
  return quotaFields.reduce<Record<string, number>>((result, field) => {
    const rawValue = limits[field.key]?.trim();

    if (!rawValue) {
      return result;
    }

    result[field.key] = Number(rawValue);
    return result;
  }, {});
}

function planStatusLabel(status: PlanSummary['status']) {
  return status === 'ACTIVE' ? '已启用' : '已停用';
}

function planStatusColor(status: PlanSummary['status']) {
  return status === 'ACTIVE' ? '#1d4ed8' : '#9f1239';
}

function planStatusBackground(status: PlanSummary['status']) {
  return status === 'ACTIVE' ? '#dbeafe' : '#ffe4e6';
}

function TenantFormCard({
  title,
  description,
  submitLabel,
  submitting,
  onSubmit,
  onCancel,
  children
}: {
  title: string;
  description: string;
  submitLabel: string;
  submitting: boolean;
  onSubmit: (event: FormEvent<HTMLFormElement>) => void;
  onCancel: () => void;
  children: ReactElement | ReactElement[];
}) {
  return (
    <section
      style={{
        background: '#fff',
        borderRadius: 20,
        padding: 20,
        boxShadow: '0 18px 50px rgba(15, 23, 42, 0.08)',
        display: 'grid',
        gap: 16
      }}
    >
      <div>
        <h3 style={{ margin: 0 }}>{title}</h3>
        <p style={{ marginBottom: 0, color: '#475569' }}>{description}</p>
      </div>
      <form onSubmit={onSubmit} style={{ display: 'grid', gap: 14 }}>
        {children}
        <div style={{ display: 'flex', gap: 12, justifyContent: 'flex-end' }}>
          <button
            type="button"
            onClick={onCancel}
            style={{
              borderRadius: 999,
              border: '1px solid #cbd5e1',
              background: '#fff',
              padding: '10px 16px',
              cursor: 'pointer'
            }}
          >
            取消
          </button>
          <button
            type="submit"
            disabled={submitting}
            style={{
              borderRadius: 999,
              border: 0,
              background: '#1d4ed8',
              color: '#fff',
              padding: '10px 18px',
              cursor: submitting ? 'progress' : 'pointer',
              opacity: submitting ? 0.8 : 1
            }}
          >
            {submitting ? '提交中...' : submitLabel}
          </button>
        </div>
      </form>
    </section>
  );
}

function Field({
  label,
  children
}: {
  label: string;
  children: ReactElement;
}) {
  return (
    <label style={{ display: 'grid', gap: 8 }}>
      <span style={{ fontWeight: 600, color: '#0f172a' }}>{label}</span>
      {children}
    </label>
  );
}

function inputStyle(multiline = false) {
  return {
    width: '100%',
    boxSizing: 'border-box' as const,
    borderRadius: 14,
    border: '1px solid #cbd5e1',
    padding: multiline ? '12px 14px' : '11px 14px',
    fontSize: 14,
    minHeight: multiline ? 110 : undefined,
    resize: multiline ? 'vertical' as const : undefined
  };
}

function TenantsPage({ session }: { session: AuthSession }) {
  const token = session.accessToken;
  const [tenants, setTenants] = useState<TenantSummary[]>([]);
  const [selectedTenantId, setSelectedTenantId] = useState<number | null>(null);
  const [selectedTenant, setSelectedTenant] = useState<TenantDetail | null>(null);
  const [loadingList, setLoadingList] = useState(true);
  const [loadingDetail, setLoadingDetail] = useState(false);
  const [submitting, setSubmitting] = useState(false);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);
  const [notice, setNotice] = useState<string | null>(null);
  const [createOpen, setCreateOpen] = useState(false);
  const [editOpen, setEditOpen] = useState(false);
  const [createForm, setCreateForm] = useState<TenantCreateFormState>(emptyCreateForm);
  const [editForm, setEditForm] = useState<TenantUpdateFormState>(emptyUpdateForm);

  const refreshList = async (preferredTenantId?: number) => {
    setLoadingList(true);
    setErrorMessage(null);

    try {
      const nextTenants = await listTenants(token);
      setTenants(nextTenants);
      const nextSelectedTenantId =
        preferredTenantId ??
        (nextTenants.some((tenant) => tenant.id === selectedTenantId)
          ? selectedTenantId
          : (nextTenants[0]?.id ?? null));
      setSelectedTenantId(nextSelectedTenantId ?? null);
    } catch (error) {
      setErrorMessage(error instanceof Error ? error.message : '租户列表加载失败。');
    } finally {
      setLoadingList(false);
    }
  };

  useEffect(() => {
    void refreshList();
  }, [token]);

  useEffect(() => {
    if (!selectedTenantId) {
      setSelectedTenant(null);
      setEditOpen(false);
      return;
    }

    let cancelled = false;

    const fetchDetail = async () => {
      setLoadingDetail(true);
      setErrorMessage(null);

      try {
        const detail = await getTenant(token, selectedTenantId);
        if (cancelled) {
          return;
        }
        setSelectedTenant(detail);
        setEditForm({
          name: detail.name,
          contactName: detail.contactName ?? '',
          contactEmail: detail.contactEmail ?? '',
          notes: detail.notes ?? ''
        });
      } catch (error) {
        if (!cancelled) {
          setErrorMessage(error instanceof Error ? error.message : '租户详情加载失败。');
        }
      } finally {
        if (!cancelled) {
          setLoadingDetail(false);
        }
      }
    };

    void fetchDetail();

    return () => {
      cancelled = true;
    };
  }, [selectedTenantId, token]);

  const updateTenantSummary = (nextSummary: TenantSummary) => {
    setTenants((currentTenants) =>
      currentTenants.map((tenant) => (tenant.id === nextSummary.id ? nextSummary : tenant))
    );
  };

  const handleCreateSubmit = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    setSubmitting(true);
    setErrorMessage(null);
    setNotice(null);

    try {
      const createdTenant = await createTenant(token, {
        code: normalizeFormValue(createForm.code),
        name: normalizeFormValue(createForm.name),
        contactName: normalizeOptionalValue(createForm.contactName),
        contactEmail: normalizeOptionalValue(createForm.contactEmail),
        notes: normalizeOptionalValue(createForm.notes),
        adminEmail: normalizeFormValue(createForm.adminEmail),
        adminDisplayName: normalizeFormValue(createForm.adminDisplayName),
        adminPassword: createForm.adminPassword
      });

      setCreateOpen(false);
      setCreateForm(emptyCreateForm());
      setNotice(`租户 ${createdTenant.name} 已创建。`);
      await refreshList(createdTenant.id);
    } catch (error) {
      setErrorMessage(error instanceof Error ? error.message : '租户创建失败。');
    } finally {
      setSubmitting(false);
    }
  };

  const handleEditSubmit = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    if (!selectedTenant) {
      return;
    }

    setSubmitting(true);
    setErrorMessage(null);
    setNotice(null);

    try {
      const updatedTenant = await updateTenant(token, selectedTenant.id, {
        name: normalizeFormValue(editForm.name),
        contactName: normalizeOptionalValue(editForm.contactName),
        contactEmail: normalizeOptionalValue(editForm.contactEmail),
        notes: normalizeOptionalValue(editForm.notes)
      });

      setSelectedTenant(updatedTenant);
      updateTenantSummary({
        id: updatedTenant.id,
        code: updatedTenant.code,
        name: updatedTenant.name,
        status: updatedTenant.status,
        contactName: updatedTenant.contactName,
        contactEmail: updatedTenant.contactEmail
      });
      setEditOpen(false);
      setNotice(`租户 ${updatedTenant.name} 已更新。`);
    } catch (error) {
      setErrorMessage(error instanceof Error ? error.message : '租户更新失败。');
    } finally {
      setSubmitting(false);
    }
  };

  const handleStatusChange = async (nextStatus: TenantSummary['status']) => {
    if (!selectedTenant) {
      return;
    }

    const confirmed =
      typeof window === 'undefined'
        ? true
        : window.confirm(
            nextStatus === 'DISABLED'
              ? '停用后该租户管理员将无法继续登录后台，是否继续？'
              : '确认重新启用该租户？'
          );

    if (!confirmed) {
      return;
    }

    setSubmitting(true);
    setErrorMessage(null);
    setNotice(null);

    try {
      const updatedSummary = await updateTenantStatus(token, selectedTenant.id, nextStatus);
      updateTenantSummary(updatedSummary);
      setSelectedTenant((currentTenant) =>
        currentTenant
          ? {
              ...currentTenant,
              status: updatedSummary.status,
              name: updatedSummary.name,
              contactName: updatedSummary.contactName,
              contactEmail: updatedSummary.contactEmail
            }
          : currentTenant
      );
      setNotice(
        nextStatus === 'DISABLED'
          ? `租户 ${updatedSummary.name} 已停用。`
          : `租户 ${updatedSummary.name} 已重新启用。`
      );
    } catch (error) {
      setErrorMessage(error instanceof Error ? error.message : '租户状态更新失败。');
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <>
      <SectionHeader
        title="租户管理"
        actions={
          <button
            type="button"
            onClick={() => {
              setCreateOpen(true);
              setEditOpen(false);
              setNotice(null);
            }}
            style={{
              borderRadius: 999,
              border: 0,
              background: '#1d4ed8',
              color: '#fff',
              padding: '10px 16px',
              cursor: 'pointer'
            }}
          >
            新建租户
          </button>
        }
      />

      {errorMessage ? (
        <div
          role="alert"
          style={{ marginBottom: 16, borderRadius: 16, background: '#fef2f2', color: '#b91c1c', padding: 14 }}
        >
          {errorMessage}
        </div>
      ) : null}

      {notice ? (
        <div style={{ marginBottom: 16, borderRadius: 16, background: '#eff6ff', color: '#1d4ed8', padding: 14 }}>{notice}</div>
      ) : null}

      <div style={{ display: 'grid', gridTemplateColumns: 'minmax(360px, 1.2fr) minmax(320px, 0.8fr)', gap: 20 }}>
        <section style={{ display: 'grid', gap: 16 }}>
          <div
            style={{
              background: '#fff',
              borderRadius: 20,
              boxShadow: '0 18px 50px rgba(15, 23, 42, 0.08)',
              overflow: 'hidden'
            }}
          >
            <div style={{ padding: 18, borderBottom: '1px solid #e2e8f0' }}>
              <strong>租户列表</strong>
              <p style={{ marginBottom: 0, color: '#64748b' }}>展示基础状态、联系人与当前可操作租户。</p>
            </div>
            {loadingList ? (
              <div style={{ padding: 18, color: '#64748b' }}>加载中...</div>
            ) : tenants.length === 0 ? (
              <div style={{ padding: 18, color: '#64748b' }}>还没有租户，先创建第一个租户。</div>
            ) : (
              <div style={{ display: 'grid' }}>
                {tenants.map((tenant) => {
                  const active = tenant.id === selectedTenantId;

                  return (
                    <button
                      key={tenant.id}
                      type="button"
                      onClick={() => {
                        setSelectedTenantId(tenant.id);
                        setEditOpen(false);
                        setNotice(null);
                      }}
                      style={{
                        display: 'grid',
                        gap: 8,
                        textAlign: 'left',
                        padding: 18,
                        border: 0,
                        borderBottom: '1px solid #e2e8f0',
                        background: active ? '#eff6ff' : '#fff',
                        cursor: 'pointer'
                      }}
                    >
                      <div style={{ display: 'flex', justifyContent: 'space-between', gap: 12, alignItems: 'center' }}>
                        <div>
                          <strong>{tenant.name}</strong>
                          <div style={{ color: '#64748b', fontSize: 13 }}>{tenant.code}</div>
                        </div>
                        <span
                          style={{
                            borderRadius: 999,
                            padding: '4px 10px',
                            fontSize: 12,
                            fontWeight: 700,
                            color: statusColor(tenant.status),
                            background: statusBackground(tenant.status)
                          }}
                        >
                          {statusLabel(tenant.status)}
                        </span>
                      </div>
                      <div style={{ color: '#334155', fontSize: 14 }}>
                        联系人：{tenant.contactName || '未设置'}
                        <br />
                        邮箱：{tenant.contactEmail || '未设置'}
                      </div>
                    </button>
                  );
                })}
              </div>
            )}
          </div>

          {createOpen ? (
            <TenantFormCard
              title="创建租户"
              description="创建租户时同步录入初始租户管理员账号。"
              submitLabel="创建租户"
              submitting={submitting}
              onSubmit={handleCreateSubmit}
              onCancel={() => {
                setCreateOpen(false);
                setCreateForm(emptyCreateForm());
              }}
            >
              <>
                <Field label="租户编码">
                  <input
                    value={createForm.code}
                    onChange={(event) => setCreateForm((current) => ({ ...current, code: event.target.value }))}
                    required
                    placeholder="tenant-acme"
                    style={inputStyle()}
                  />
                </Field>
                <Field label="租户名称">
                  <input
                    value={createForm.name}
                    onChange={(event) => setCreateForm((current) => ({ ...current, name: event.target.value }))}
                    required
                    placeholder="Acme Inc."
                    style={inputStyle()}
                  />
                </Field>
                <Field label="联系人">
                  <input
                    value={createForm.contactName}
                    onChange={(event) => setCreateForm((current) => ({ ...current, contactName: event.target.value }))}
                    placeholder="Alice"
                    style={inputStyle()}
                  />
                </Field>
                <Field label="联系邮箱">
                  <input
                    type="email"
                    value={createForm.contactEmail}
                    onChange={(event) => setCreateForm((current) => ({ ...current, contactEmail: event.target.value }))}
                    placeholder="alice@acme.test"
                    style={inputStyle()}
                  />
                </Field>
                <Field label="备注">
                  <textarea
                    value={createForm.notes}
                    onChange={(event) => setCreateForm((current) => ({ ...current, notes: event.target.value }))}
                    placeholder="记录租户背景、交付状态或风险备注。"
                    style={inputStyle(true)}
                  />
                </Field>
                <Field label="初始管理员邮箱">
                  <input
                    type="email"
                    value={createForm.adminEmail}
                    onChange={(event) => setCreateForm((current) => ({ ...current, adminEmail: event.target.value }))}
                    required
                    placeholder="owner@acme.test"
                    style={inputStyle()}
                  />
                </Field>
                <Field label="初始管理员名称">
                  <input
                    value={createForm.adminDisplayName}
                    onChange={(event) => setCreateForm((current) => ({ ...current, adminDisplayName: event.target.value }))}
                    required
                    placeholder="Acme Owner"
                    style={inputStyle()}
                  />
                </Field>
                <Field label="初始管理员密码">
                  <input
                    type="password"
                    value={createForm.adminPassword}
                    onChange={(event) => setCreateForm((current) => ({ ...current, adminPassword: event.target.value }))}
                    required
                    placeholder="Tenant123!"
                    style={inputStyle()}
                  />
                </Field>
              </>
            </TenantFormCard>
          ) : null}
        </section>

        <section style={{ display: 'grid', gap: 16 }}>
          <div
            style={{
              background: '#fff',
              borderRadius: 20,
              padding: 20,
              boxShadow: '0 18px 50px rgba(15, 23, 42, 0.08)',
              minHeight: 280,
              display: 'grid',
              gap: 16,
              alignContent: 'start'
            }}
          >
            <div style={{ display: 'flex', justifyContent: 'space-between', gap: 12, alignItems: 'center' }}>
              <div>
                <h3 style={{ margin: 0 }}>租户详情</h3>
                <p style={{ marginBottom: 0, color: '#64748b' }}>查看基础信息、管理员与使用提示。</p>
              </div>
              {selectedTenant ? (
                <button
                  type="button"
                  onClick={() => {
                    setEditOpen((current) => !current);
                    setCreateOpen(false);
                  }}
                  style={{
                    borderRadius: 999,
                    border: '1px solid #cbd5e1',
                    background: '#fff',
                    padding: '10px 14px',
                    cursor: 'pointer'
                  }}
                >
                  {editOpen ? '收起编辑' : '编辑租户'}
                </button>
              ) : null}
            </div>

            {loadingDetail ? (
              <div style={{ color: '#64748b' }}>详情加载中...</div>
            ) : !selectedTenant ? (
              <div style={{ color: '#64748b' }}>从左侧选择一个租户查看详情。</div>
            ) : (
              <>
                <div style={{ display: 'flex', justifyContent: 'space-between', gap: 12, alignItems: 'center' }}>
                  <div>
                    <h4 style={{ margin: 0 }}>{selectedTenant.name}</h4>
                    <div style={{ color: '#64748b', marginTop: 4 }}>{selectedTenant.code}</div>
                  </div>
                  <span
                    style={{
                      borderRadius: 999,
                      padding: '5px 10px',
                      fontSize: 12,
                      fontWeight: 700,
                      color: statusColor(selectedTenant.status),
                      background: statusBackground(selectedTenant.status)
                    }}
                  >
                    {statusLabel(selectedTenant.status)}
                  </span>
                </div>

                <div style={{ display: 'grid', gap: 10, color: '#334155' }}>
                  <div>联系人：{selectedTenant.contactName || '未设置'}</div>
                  <div>联系邮箱：{selectedTenant.contactEmail || '未设置'}</div>
                  <div>备注：{selectedTenant.notes || '暂无备注'}</div>
                </div>

                <div style={{ borderTop: '1px solid #e2e8f0', paddingTop: 16, display: 'grid', gap: 8 }}>
                  <strong>初始管理员</strong>
                  <div style={{ color: '#334155' }}>
                    {selectedTenant.admin
                      ? `${selectedTenant.admin.displayName} · ${selectedTenant.admin.email}`
                      : '暂无管理员信息'}
                  </div>
                </div>

                <div style={{ borderTop: '1px solid #e2e8f0', paddingTop: 16, display: 'grid', gap: 8 }}>
                  <strong>用量摘要</strong>
                  <div style={{ color: '#64748b' }}>当前版本先展示租户基础信息，用量统计将在套餐与额度模块联动接入。</div>
                </div>

                <div style={{ display: 'flex', gap: 12, flexWrap: 'wrap' }}>
                  <button
                    type="button"
                    disabled={submitting || selectedTenant.status === 'ACTIVE'}
                    onClick={() => void handleStatusChange('ACTIVE')}
                    style={{
                      borderRadius: 999,
                      border: 0,
                      background: '#166534',
                      color: '#fff',
                      padding: '10px 16px',
                      cursor: submitting || selectedTenant.status === 'ACTIVE' ? 'not-allowed' : 'pointer',
                      opacity: submitting || selectedTenant.status === 'ACTIVE' ? 0.6 : 1
                    }}
                  >
                    启用租户
                  </button>
                  <button
                    type="button"
                    disabled={submitting || selectedTenant.status === 'DISABLED'}
                    onClick={() => void handleStatusChange('DISABLED')}
                    style={{
                      borderRadius: 999,
                      border: 0,
                      background: '#b91c1c',
                      color: '#fff',
                      padding: '10px 16px',
                      cursor: submitting || selectedTenant.status === 'DISABLED' ? 'not-allowed' : 'pointer',
                      opacity: submitting || selectedTenant.status === 'DISABLED' ? 0.6 : 1
                    }}
                  >
                    停用租户
                  </button>
                </div>
              </>
            )}
          </div>

          {editOpen && selectedTenant ? (
            <TenantFormCard
              title="编辑租户"
              description="更新租户名称、联系人与备注信息。"
              submitLabel="保存修改"
              submitting={submitting}
              onSubmit={handleEditSubmit}
              onCancel={() => setEditOpen(false)}
            >
              <>
                <Field label="租户名称">
                  <input
                    value={editForm.name}
                    onChange={(event) => setEditForm((current) => ({ ...current, name: event.target.value }))}
                    required
                    style={inputStyle()}
                  />
                </Field>
                <Field label="联系人">
                  <input
                    value={editForm.contactName}
                    onChange={(event) => setEditForm((current) => ({ ...current, contactName: event.target.value }))}
                    style={inputStyle()}
                  />
                </Field>
                <Field label="联系邮箱">
                  <input
                    type="email"
                    value={editForm.contactEmail}
                    onChange={(event) => setEditForm((current) => ({ ...current, contactEmail: event.target.value }))}
                    style={inputStyle()}
                  />
                </Field>
                <Field label="备注">
                  <textarea
                    value={editForm.notes}
                    onChange={(event) => setEditForm((current) => ({ ...current, notes: event.target.value }))}
                    style={inputStyle(true)}
                  />
                </Field>
              </>
            </TenantFormCard>
          ) : null}
        </section>
      </div>
    </>
  );
}

function PlansPage({ session }: { session: AuthSession }) {
  const token = session.accessToken;
  const [plans, setPlans] = useState<PlanSummary[]>([]);
  const [tenants, setTenants] = useState<TenantSummary[]>([]);
  const [selectedAssignment, setSelectedAssignment] = useState<{ tenantId: number; planId: number; overrides: Record<string, number> } | null>(null);
  const [loadingAssignment, setLoadingAssignment] = useState(false);
  const [selectedPlanId, setSelectedPlanId] = useState<number | null>(null);
  const [loading, setLoading] = useState(true);
  const [submitting, setSubmitting] = useState(false);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);
  const [notice, setNotice] = useState<string | null>(null);
  const [createOpen, setCreateOpen] = useState(false);
  const [editOpen, setEditOpen] = useState(false);
  const [createForm, setCreateForm] = useState<PlanCreateFormState>({
    code: '',
    name: '',
    limits: buildLimitFormState()
  });
  const [editForm, setEditForm] = useState<PlanUpdateFormState>({
    name: '',
    limits: buildLimitFormState()
  });
  const [assignmentTenantId, setAssignmentTenantId] = useState('');
  const [assignmentPlanId, setAssignmentPlanId] = useState('');
  const [assignmentOverrides, setAssignmentOverrides] = useState<Record<string, string>>(buildLimitFormState());

  const selectedPlan = plans.find((plan) => plan.id === selectedPlanId) ?? null;

  const loadData = async (preferredPlanId?: number) => {
    setLoading(true);
    setErrorMessage(null);

    try {
      const [nextPlans, nextTenants] = await Promise.all([listPlans(token), listTenants(token)]);
      setPlans(nextPlans);
      setTenants(nextTenants);
      const nextSelectedPlanId =
        preferredPlanId ??
        (nextPlans.some((plan) => plan.id === selectedPlanId) ? selectedPlanId : (nextPlans[0]?.id ?? null));
      setSelectedPlanId(nextSelectedPlanId ?? null);
      if (!assignmentTenantId && nextTenants[0]) {
        setAssignmentTenantId(String(nextTenants[0].id));
      }
      if (!assignmentPlanId && nextPlans[0]) {
        setAssignmentPlanId(String(nextPlans[0].id));
      }
    } catch (error) {
      setErrorMessage(error instanceof Error ? error.message : '套餐数据加载失败。');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    void loadData();
  }, [token]);

  useEffect(() => {
    if (!selectedPlan) {
      setEditForm({ name: '', limits: buildLimitFormState() });
      return;
    }

    setEditForm({
      name: selectedPlan.name,
      limits: buildLimitFormState(selectedPlan.limits)
    });

    if (!assignmentPlanId) {
      setAssignmentPlanId(String(selectedPlan.id));
    }
  }, [selectedPlan]);

  useEffect(() => {
    if (!assignmentTenantId) {
      setSelectedAssignment(null);
      return;
    }

    let cancelled = false;

    const loadAssignment = async () => {
      setLoadingAssignment(true);

      try {
        const assignment = await getTenantPlanAssignment(token, Number(assignmentTenantId));

        if (cancelled) {
          return;
        }

        setSelectedAssignment(assignment);
        setAssignmentPlanId(String(assignment.planId));
        setAssignmentOverrides(buildLimitFormState(assignment.overrides));
      } catch {
        if (!cancelled) {
          setSelectedAssignment(null);
          setAssignmentOverrides(buildLimitFormState());
        }
      } finally {
        if (!cancelled) {
          setLoadingAssignment(false);
        }
      }
    };

    void loadAssignment();

    return () => {
      cancelled = true;
    };
  }, [assignmentTenantId, token]);

  const updatePlanInList = (nextPlan: PlanSummary) => {
    setPlans((currentPlans) => currentPlans.map((plan) => (plan.id === nextPlan.id ? nextPlan : plan)));
  };

  const handleCreatePlan = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    setSubmitting(true);
    setErrorMessage(null);
    setNotice(null);

    try {
      const createdPlan = await createPlan(token, {
        code: normalizeFormValue(createForm.code),
        name: normalizeFormValue(createForm.name),
        limits: parseLimitFormState(createForm.limits)
      });
      setCreateOpen(false);
      setCreateForm({ code: '', name: '', limits: buildLimitFormState() });
      setNotice(`套餐 ${createdPlan.name} 已创建。`);
      await loadData(createdPlan.id);
      setAssignmentPlanId(String(createdPlan.id));
    } catch (error) {
      setErrorMessage(error instanceof Error ? error.message : '套餐创建失败。');
    } finally {
      setSubmitting(false);
    }
  };

  const handleEditPlan = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    if (!selectedPlan) {
      return;
    }

    setSubmitting(true);
    setErrorMessage(null);
    setNotice(null);

    try {
      const updatedPlan = await updatePlan(token, selectedPlan.id, {
        name: normalizeFormValue(editForm.name),
        limits: parseLimitFormState(editForm.limits)
      });
      updatePlanInList(updatedPlan);
      setEditOpen(false);
      setNotice(`套餐 ${updatedPlan.name} 已更新。`);
    } catch (error) {
      setErrorMessage(error instanceof Error ? error.message : '套餐更新失败。');
    } finally {
      setSubmitting(false);
    }
  };

  const handlePlanStatusChange = async (nextStatus: PlanSummary['status']) => {
    if (!selectedPlan) {
      return;
    }

    const confirmed =
      typeof window === 'undefined'
        ? true
        : window.confirm(
            nextStatus === 'DISABLED'
              ? '停用后新租户将不应继续分配到该套餐，是否继续？'
              : '确认重新启用该套餐？'
          );

    if (!confirmed) {
      return;
    }

    setSubmitting(true);
    setErrorMessage(null);
    setNotice(null);

    try {
      const updatedPlan = await updatePlanStatus(token, selectedPlan.id, nextStatus);
      updatePlanInList(updatedPlan);
      setNotice(
        nextStatus === 'DISABLED'
          ? `套餐 ${updatedPlan.name} 已停用。`
          : `套餐 ${updatedPlan.name} 已启用。`
      );
    } catch (error) {
      setErrorMessage(error instanceof Error ? error.message : '套餐状态更新失败。');
    } finally {
      setSubmitting(false);
    }
  };

  const handleAssignmentSubmit = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();

    if (!assignmentTenantId || !assignmentPlanId) {
      setErrorMessage('请选择租户和套餐后再提交分配。');
      return;
    }

    setSubmitting(true);
    setErrorMessage(null);
    setNotice(null);

    try {
      const nextAssignment = await assignTenantPlan(token, {
        tenantId: Number(assignmentTenantId),
        planId: Number(assignmentPlanId),
        overrides: parseLimitFormState(assignmentOverrides)
      });
      setSelectedAssignment(nextAssignment);

      const tenantName = tenants.find((tenant) => tenant.id === Number(assignmentTenantId))?.name ?? `#${assignmentTenantId}`;
      const planName = plans.find((plan) => plan.id === Number(assignmentPlanId))?.name ?? `#${assignmentPlanId}`;
      setNotice(`已为租户 ${tenantName} 分配套餐 ${planName}。`);
    } catch (error) {
      setErrorMessage(error instanceof Error ? error.message : '套餐分配失败。');
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <>
      <SectionHeader
        title="套餐与额度"
        actions={
          <button
            type="button"
            onClick={() => {
              setCreateOpen(true);
              setEditOpen(false);
              setNotice(null);
            }}
            style={{
              borderRadius: 999,
              border: 0,
              background: '#1d4ed8',
              color: '#fff',
              padding: '10px 16px',
              cursor: 'pointer'
            }}
          >
            新建套餐
          </button>
        }
      />

      {errorMessage ? (
        <div role="alert" style={{ marginBottom: 16, borderRadius: 16, background: '#fef2f2', color: '#b91c1c', padding: 14 }}>
          {errorMessage}
        </div>
      ) : null}

      {notice ? (
        <div style={{ marginBottom: 16, borderRadius: 16, background: '#eff6ff', color: '#1d4ed8', padding: 14 }}>{notice}</div>
      ) : null}

      <div style={{ display: 'grid', gridTemplateColumns: 'minmax(360px, 1fr) minmax(360px, 1fr)', gap: 20 }}>
        <section style={{ display: 'grid', gap: 16 }}>
          <div
            style={{
              background: '#fff',
              borderRadius: 20,
              boxShadow: '0 18px 50px rgba(15, 23, 42, 0.08)',
              overflow: 'hidden'
            }}
          >
            <div style={{ padding: 18, borderBottom: '1px solid #e2e8f0' }}>
              <strong>套餐列表</strong>
              <p style={{ marginBottom: 0, color: '#64748b' }}>管理平台可分配的套餐和基础限额。</p>
            </div>
            {loading ? (
              <div style={{ padding: 18, color: '#64748b' }}>加载中...</div>
            ) : plans.length === 0 ? (
              <div style={{ padding: 18, color: '#64748b' }}>还没有套餐，先创建一个基础套餐。</div>
            ) : (
              <div style={{ display: 'grid' }}>
                {plans.map((plan) => {
                  const active = plan.id === selectedPlanId;

                  return (
                    <button
                      key={plan.id}
                      type="button"
                      onClick={() => {
                        setSelectedPlanId(plan.id);
                        setEditOpen(false);
                        setNotice(null);
                      }}
                      style={{
                        display: 'grid',
                        gap: 8,
                        textAlign: 'left',
                        padding: 18,
                        border: 0,
                        borderBottom: '1px solid #e2e8f0',
                        background: active ? '#eff6ff' : '#fff',
                        cursor: 'pointer'
                      }}
                    >
                      <div style={{ display: 'flex', justifyContent: 'space-between', gap: 12, alignItems: 'center' }}>
                        <div>
                          <strong>{plan.name}</strong>
                          <div style={{ color: '#64748b', fontSize: 13 }}>{plan.code}</div>
                        </div>
                        <span
                          style={{
                            borderRadius: 999,
                            padding: '4px 10px',
                            fontSize: 12,
                            fontWeight: 700,
                            color: planStatusColor(plan.status),
                            background: planStatusBackground(plan.status)
                          }}
                        >
                          {planStatusLabel(plan.status)}
                        </span>
                      </div>
                      <div style={{ display: 'flex', gap: 8, flexWrap: 'wrap' }}>
                        {quotaFields.map((field) => (
                          <span
                            key={field.key}
                            style={{
                              borderRadius: 999,
                              padding: '4px 8px',
                              background: '#f8fafc',
                              color: '#334155',
                              fontSize: 12
                            }}
                          >
                            {field.label} {plan.limits[field.key] ?? '-'}
                          </span>
                        ))}
                      </div>
                    </button>
                  );
                })}
              </div>
            )}
          </div>

          {createOpen ? (
            <TenantFormCard
              title="创建套餐"
              description="定义默认限额，后续可在租户维度做覆盖。"
              submitLabel="创建套餐"
              submitting={submitting}
              onSubmit={handleCreatePlan}
              onCancel={() => {
                setCreateOpen(false);
                setCreateForm({ code: '', name: '', limits: buildLimitFormState() });
              }}
            >
              <>
                <Field label="套餐编码">
                  <input
                    value={createForm.code}
                    onChange={(event) => setCreateForm((current) => ({ ...current, code: event.target.value }))}
                    required
                    placeholder="starter"
                    style={inputStyle()}
                  />
                </Field>
                <Field label="套餐名称">
                  <input
                    value={createForm.name}
                    onChange={(event) => setCreateForm((current) => ({ ...current, name: event.target.value }))}
                    required
                    placeholder="Starter"
                    style={inputStyle()}
                  />
                </Field>
                {quotaFields.map((field) => (
                  <Field key={field.key} label={field.label}>
                    <input
                      type="number"
                      min="0"
                      value={createForm.limits[field.key]}
                      onChange={(event) =>
                        setCreateForm((current) => ({
                          ...current,
                          limits: { ...current.limits, [field.key]: event.target.value }
                        }))
                      }
                      style={inputStyle()}
                    />
                  </Field>
                ))}
              </>
            </TenantFormCard>
          ) : null}
        </section>

        <section style={{ display: 'grid', gap: 16 }}>
          <div
            style={{
              background: '#fff',
              borderRadius: 20,
              padding: 20,
              boxShadow: '0 18px 50px rgba(15, 23, 42, 0.08)',
              display: 'grid',
              gap: 16
            }}
          >
            <div style={{ display: 'flex', justifyContent: 'space-between', gap: 12, alignItems: 'center' }}>
              <div>
                <h3 style={{ margin: 0 }}>套餐详情</h3>
                <p style={{ marginBottom: 0, color: '#64748b' }}>查看当前套餐限额并进行启停或编辑。</p>
              </div>
              {selectedPlan ? (
                <button
                  type="button"
                  onClick={() => {
                    setEditOpen((current) => !current);
                    setCreateOpen(false);
                  }}
                  style={{
                    borderRadius: 999,
                    border: '1px solid #cbd5e1',
                    background: '#fff',
                    padding: '10px 14px',
                    cursor: 'pointer'
                  }}
                >
                  {editOpen ? '收起编辑' : '编辑套餐'}
                </button>
              ) : null}
            </div>

            {!selectedPlan ? (
              <div style={{ color: '#64748b' }}>从左侧选择一个套餐查看详情。</div>
            ) : (
              <>
                <div style={{ display: 'flex', justifyContent: 'space-between', gap: 12, alignItems: 'center' }}>
                  <div>
                    <h4 style={{ margin: 0 }}>{selectedPlan.name}</h4>
                    <div style={{ color: '#64748b', marginTop: 4 }}>{selectedPlan.code}</div>
                  </div>
                  <span
                    style={{
                      borderRadius: 999,
                      padding: '5px 10px',
                      fontSize: 12,
                      fontWeight: 700,
                      color: planStatusColor(selectedPlan.status),
                      background: planStatusBackground(selectedPlan.status)
                    }}
                  >
                    {planStatusLabel(selectedPlan.status)}
                  </span>
                </div>

                <div style={{ display: 'grid', gap: 10 }}>
                  {quotaFields.map((field) => (
                    <div key={field.key} style={{ display: 'flex', justifyContent: 'space-between', gap: 12, color: '#334155' }}>
                      <span>{field.label}</span>
                      <strong>{selectedPlan.limits[field.key] ?? '-'}</strong>
                    </div>
                  ))}
                </div>

                <div style={{ display: 'flex', gap: 12, flexWrap: 'wrap' }}>
                  <button
                    type="button"
                    disabled={submitting || selectedPlan.status === 'ACTIVE'}
                    onClick={() => void handlePlanStatusChange('ACTIVE')}
                    style={{
                      borderRadius: 999,
                      border: 0,
                      background: '#1d4ed8',
                      color: '#fff',
                      padding: '10px 16px',
                      cursor: submitting || selectedPlan.status === 'ACTIVE' ? 'not-allowed' : 'pointer',
                      opacity: submitting || selectedPlan.status === 'ACTIVE' ? 0.6 : 1
                    }}
                  >
                    启用套餐
                  </button>
                  <button
                    type="button"
                    disabled={submitting || selectedPlan.status === 'DISABLED'}
                    onClick={() => void handlePlanStatusChange('DISABLED')}
                    style={{
                      borderRadius: 999,
                      border: 0,
                      background: '#be123c',
                      color: '#fff',
                      padding: '10px 16px',
                      cursor: submitting || selectedPlan.status === 'DISABLED' ? 'not-allowed' : 'pointer',
                      opacity: submitting || selectedPlan.status === 'DISABLED' ? 0.6 : 1
                    }}
                  >
                    停用套餐
                  </button>
                </div>
              </>
            )}
          </div>

          {editOpen && selectedPlan ? (
            <TenantFormCard
              title="编辑套餐"
              description="更新套餐名称和默认限额。"
              submitLabel="保存套餐"
              submitting={submitting}
              onSubmit={handleEditPlan}
              onCancel={() => setEditOpen(false)}
            >
              <>
                <Field label="套餐名称">
                  <input
                    value={editForm.name}
                    onChange={(event) => setEditForm((current) => ({ ...current, name: event.target.value }))}
                    required
                    style={inputStyle()}
                  />
                </Field>
                {quotaFields.map((field) => (
                  <Field key={field.key} label={field.label}>
                    <input
                      type="number"
                      min="0"
                      value={editForm.limits[field.key]}
                      onChange={(event) =>
                        setEditForm((current) => ({
                          ...current,
                          limits: { ...current.limits, [field.key]: event.target.value }
                        }))
                      }
                      style={inputStyle()}
                    />
                  </Field>
                ))}
              </>
            </TenantFormCard>
          ) : null}

          <TenantFormCard
            title="租户套餐分配"
            description="为指定租户分配套餐，并按资源维度设置覆盖值。留空表示不覆盖。"
            submitLabel="保存分配"
            submitting={submitting}
            onSubmit={handleAssignmentSubmit}
            onCancel={() => {
              setAssignmentOverrides(buildLimitFormState());
              setNotice(null);
            }}
          >
            <>
              <Field label="租户">
                <select
                  value={assignmentTenantId}
                  onChange={(event) => {
                    setAssignmentTenantId(event.target.value);
                    setNotice(null);
                  }}
                  style={inputStyle()}
                >
                  <option value="">请选择租户</option>
                  {tenants.map((tenant) => (
                    <option key={tenant.id} value={tenant.id}>
                      {tenant.name}
                    </option>
                  ))}
                </select>
              </Field>
              <div style={{ borderRadius: 16, background: '#f8fafc', padding: 14, color: '#334155' }}>
                {loadingAssignment ? (
                  '正在加载当前套餐分配...'
                ) : !assignmentTenantId ? (
                  '请选择租户后查看当前分配。'
                ) : !selectedAssignment ? (
                  '该租户当前还没有分配套餐。'
                ) : (
                  <>
                    当前套餐：{plans.find((plan) => plan.id === selectedAssignment.planId)?.name ?? `#${selectedAssignment.planId}`}
                    <br />
                    覆盖项：
                    {Object.keys(selectedAssignment.overrides).length === 0
                      ? '无，沿用套餐默认值'
                      : quotaFields
                          .filter((field) => selectedAssignment.overrides[field.key] != null)
                          .map((field) => `${field.label} ${selectedAssignment.overrides[field.key]}`)
                          .join('，')}
                  </>
                )}
              </div>
              <Field label="套餐">
                <select
                  value={assignmentPlanId}
                  onChange={(event) => setAssignmentPlanId(event.target.value)}
                  style={inputStyle()}
                >
                  <option value="">请选择套餐</option>
                  {plans.map((plan) => (
                    <option key={plan.id} value={plan.id}>
                      {plan.name}
                    </option>
                  ))}
                </select>
              </Field>
              {quotaFields.map((field) => (
                <Field key={field.key} label={`${field.label} 覆盖值`}>
                  <input
                    type="number"
                    min="0"
                    value={assignmentOverrides[field.key]}
                    onChange={(event) =>
                      setAssignmentOverrides((current) => ({
                        ...current,
                        [field.key]: event.target.value
                      }))
                    }
                    placeholder="留空表示沿用套餐默认值"
                    style={inputStyle()}
                  />
                </Field>
              ))}
            </>
          </TenantFormCard>
        </section>
      </div>
    </>
  );
}

function AuditLogsPage({ session }: { session: AuthSession }) {
  const token = session.accessToken;
  const [logs, setLogs] = useState<AuditLogSummary[]>([]);
  const [selectedLogId, setSelectedLogId] = useState<number | null>(null);
  const [selectedLog, setSelectedLog] = useState<AuditLogDetail | null>(null);
  const [loadingList, setLoadingList] = useState(true);
  const [loadingDetail, setLoadingDetail] = useState(false);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);
  const [filters, setFilters] = useState({
    tenantId: '',
    actorUserId: '',
    actionType: '',
    result: '',
    riskLevel: '',
    createdFrom: '',
    createdTo: ''
  });

  const filteredHighRiskCount = logs.filter((log) => log.riskLevel === 'HIGH').length;
  const filteredSuccessCount = logs.filter((log) => log.result === 'SUCCESS').length;

  const loadLogs = async () => {
    setLoadingList(true);
    setErrorMessage(null);

    try {
      const nextLogs = await listAuditLogs(token, {
        tenantId: filters.tenantId ? Number(filters.tenantId) : undefined,
        actorUserId: filters.actorUserId ? Number(filters.actorUserId) : undefined,
        actionType: filters.actionType || undefined,
        result: filters.result || undefined,
        riskLevel: filters.riskLevel || undefined,
        createdFrom: filters.createdFrom ? new Date(filters.createdFrom).toISOString() : undefined,
        createdTo: filters.createdTo ? new Date(filters.createdTo).toISOString() : undefined
      });
      setLogs(nextLogs);
      setSelectedLogId((currentSelectedLogId) =>
        nextLogs.some((log) => log.id === currentSelectedLogId) ? currentSelectedLogId : (nextLogs[0]?.id ?? null)
      );
    } catch (error) {
      setErrorMessage(error instanceof Error ? error.message : '审计日志加载失败。');
    } finally {
      setLoadingList(false);
    }
  };

  useEffect(() => {
    void loadLogs();
  }, [token]);

  useEffect(() => {
    if (!selectedLogId) {
      setSelectedLog(null);
      return;
    }

    let cancelled = false;

    const loadDetail = async () => {
      setLoadingDetail(true);
      setErrorMessage(null);

      try {
        const detail = await getAuditLog(token, selectedLogId);
        if (!cancelled) {
          setSelectedLog(detail);
        }
      } catch (error) {
        if (!cancelled) {
          setErrorMessage(error instanceof Error ? error.message : '审计详情加载失败。');
        }
      } finally {
        if (!cancelled) {
          setLoadingDetail(false);
        }
      }
    };

    void loadDetail();

    return () => {
      cancelled = true;
    };
  }, [selectedLogId, token]);

  const handleFilterSubmit = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    await loadLogs();
  };

  const handleResetFilters = async () => {
    setFilters({
      tenantId: '',
      actorUserId: '',
      actionType: '',
      result: '',
      riskLevel: '',
      createdFrom: '',
      createdTo: ''
    });

    setLoadingList(true);
    setErrorMessage(null);

    try {
      const nextLogs = await listAuditLogs(token, {});
      setLogs(nextLogs);
      setSelectedLogId(nextLogs[0]?.id ?? null);
    } catch (error) {
      setErrorMessage(error instanceof Error ? error.message : '审计日志加载失败。');
    } finally {
      setLoadingList(false);
    }
  };

  return (
    <>
      <SectionHeader title="审计日志" />

      {errorMessage ? (
        <div role="alert" style={{ marginBottom: 16, borderRadius: 16, background: '#fef2f2', color: '#b91c1c', padding: 14 }}>
          {errorMessage}
        </div>
      ) : null}

      <div style={{ display: 'grid', gap: 16, marginBottom: 20 }}>
        <TenantFormCard
          title="筛选条件"
          description="按时间、用户、动作、结果和租户筛选审计记录。"
          submitLabel="应用筛选"
          submitting={loadingList}
          onSubmit={handleFilterSubmit}
          onCancel={() => {
            void handleResetFilters();
          }}
        >
          <>
            <Field label="租户 ID">
              <input
                value={filters.tenantId}
                onChange={(event) => setFilters((current) => ({ ...current, tenantId: event.target.value }))}
                style={inputStyle()}
              />
            </Field>
            <Field label="操作人 ID">
              <input
                value={filters.actorUserId}
                onChange={(event) => setFilters((current) => ({ ...current, actorUserId: event.target.value }))}
                style={inputStyle()}
              />
            </Field>
            <Field label="动作类型">
              <input
                value={filters.actionType}
                onChange={(event) => setFilters((current) => ({ ...current, actionType: event.target.value }))}
                placeholder="例如 TENANT_CREATED"
                style={inputStyle()}
              />
            </Field>
            <Field label="结果">
              <select
                value={filters.result}
                onChange={(event) => setFilters((current) => ({ ...current, result: event.target.value }))}
                style={inputStyle()}
              >
                <option value="">全部结果</option>
                <option value="SUCCESS">SUCCESS</option>
                <option value="FAILED">FAILED</option>
                <option value="PARTIAL_SUCCESS">PARTIAL_SUCCESS</option>
              </select>
            </Field>
            <Field label="风险级别">
              <select
                value={filters.riskLevel}
                onChange={(event) => setFilters((current) => ({ ...current, riskLevel: event.target.value }))}
                style={inputStyle()}
              >
                <option value="">全部风险</option>
                <option value="LOW">LOW</option>
                <option value="MEDIUM">MEDIUM</option>
                <option value="HIGH">HIGH</option>
              </select>
            </Field>
            <Field label="开始时间">
              <input
                type="datetime-local"
                value={filters.createdFrom}
                onChange={(event) => setFilters((current) => ({ ...current, createdFrom: event.target.value }))}
                style={inputStyle()}
              />
            </Field>
            <Field label="结束时间">
              <input
                type="datetime-local"
                value={filters.createdTo}
                onChange={(event) => setFilters((current) => ({ ...current, createdTo: event.target.value }))}
                style={inputStyle()}
              />
            </Field>
          </>
        </TenantFormCard>
        <div style={{ display: 'grid', gridTemplateColumns: 'repeat(3, minmax(0, 1fr))', gap: 16 }}>
          <StatCard title="当前结果数" value={String(logs.length)} description="基于当前筛选条件返回。" />
          <StatCard title="高风险条数" value={String(filteredHighRiskCount)} description="当前结果集中的 HIGH 风险日志。" />
          <StatCard
            title="成功率"
            value={logs.length === 0 ? '-' : `${Math.round((filteredSuccessCount / logs.length) * 100)}%`}
            description="按当前结果集的 result=SUCCESS 计算。"
          />
        </div>
      </div>

      <div style={{ display: 'grid', gridTemplateColumns: 'minmax(360px, 1fr) minmax(360px, 1fr)', gap: 20 }}>
        <div
          style={{
            background: '#fff',
            borderRadius: 20,
            boxShadow: '0 18px 50px rgba(15, 23, 42, 0.08)',
            overflow: 'hidden'
          }}
        >
          <div style={{ padding: 18, borderBottom: '1px solid #e2e8f0' }}>
            <strong>日志列表</strong>
            <p style={{ marginBottom: 0, color: '#64748b' }}>展示动作、结果、风险和时间。</p>
          </div>
          {loadingList ? (
            <div style={{ padding: 18, color: '#64748b' }}>加载中...</div>
          ) : logs.length === 0 ? (
            <div style={{ padding: 18, color: '#64748b' }}>当前筛选条件下没有审计日志。</div>
          ) : (
            <div style={{ display: 'grid' }}>
              {logs.map((log) => {
                const active = log.id === selectedLogId;

                return (
                  <button
                    key={log.id}
                    type="button"
                    onClick={() => setSelectedLogId(log.id)}
                    style={{
                      display: 'grid',
                      gap: 8,
                      textAlign: 'left',
                      padding: 18,
                      border: 0,
                      borderBottom: '1px solid #e2e8f0',
                      background: active ? '#eff6ff' : '#fff',
                      cursor: 'pointer'
                    }}
                  >
                    <div style={{ display: 'flex', justifyContent: 'space-between', gap: 12 }}>
                      <strong>{log.actionType}</strong>
                      <span style={{ color: '#475569', fontSize: 12 }}>{new Date(log.createdAt).toLocaleString()}</span>
                    </div>
                      <div style={{ display: 'flex', gap: 8, flexWrap: 'wrap' }}>
                        <span style={{ borderRadius: 999, padding: '4px 8px', background: log.result === 'SUCCESS' ? '#dcfce7' : '#fee2e2', color: log.result === 'SUCCESS' ? '#166534' : '#991b1b', fontSize: 12, fontWeight: 700 }}>
                          {log.result}
                        </span>
                        <span style={{ borderRadius: 999, padding: '4px 8px', background: log.riskLevel === 'HIGH' ? '#fee2e2' : log.riskLevel === 'MEDIUM' ? '#fef3c7' : '#dbeafe', color: log.riskLevel === 'HIGH' ? '#991b1b' : log.riskLevel === 'MEDIUM' ? '#92400e' : '#1d4ed8', fontSize: 12, fontWeight: 700 }}>
                          {log.riskLevel}
                        </span>
                      </div>
                    <div style={{ color: '#334155', fontSize: 14 }}>
                        目标：{log.targetType} {log.targetId ?? '-'}
                        <br />
                        租户：{log.tenantId ?? '平台级'} · 操作人：{log.actorUserId ?? '匿名'}
                    </div>
                  </button>
                );
              })}
            </div>
          )}
        </div>

        <div
          style={{
            background: '#fff',
            borderRadius: 20,
            padding: 20,
            boxShadow: '0 18px 50px rgba(15, 23, 42, 0.08)',
            display: 'grid',
            gap: 16,
            alignContent: 'start'
          }}
        >
          <div>
            <h3 style={{ margin: 0 }}>日志详情</h3>
            <p style={{ marginBottom: 0, color: '#64748b' }}>查看资源、风险级别和上下文。</p>
          </div>

          {loadingDetail ? (
            <div style={{ color: '#64748b' }}>详情加载中...</div>
          ) : !selectedLog ? (
            <div style={{ color: '#64748b' }}>从左侧选择一条审计日志查看详情。</div>
          ) : (
            <>
              <div style={{ display: 'grid', gap: 10, color: '#334155' }}>
                <div>动作：{selectedLog.actionType}</div>
                <div>结果：{selectedLog.result}</div>
                <div>风险级别：{selectedLog.riskLevel}</div>
                <div>租户 ID：{selectedLog.tenantId ?? '平台级'}</div>
                <div>操作人 ID：{selectedLog.actorUserId ?? '匿名'}</div>
                <div>目标类型：{selectedLog.targetType}</div>
                <div>目标 ID：{selectedLog.targetId ?? '-'}</div>
                <div>时间：{new Date(selectedLog.createdAt).toLocaleString()}</div>
              </div>

              <div style={{ borderTop: '1px solid #e2e8f0', paddingTop: 16 }}>
                <strong>上下文</strong>
                <pre
                  style={{
                    marginTop: 12,
                    marginBottom: 0,
                    borderRadius: 16,
                    background: '#0f172a',
                    color: '#e2e8f0',
                    padding: 16,
                    overflowX: 'auto',
                    fontSize: 12,
                    lineHeight: 1.5
                  }}
                >
                  {JSON.stringify(selectedLog.context, null, 2)}
                </pre>
              </div>
            </>
          )}
        </div>
      </div>
    </>
  );
}

function ProtectedApp({ session, onLogout }: { session: AuthSession; onLogout: () => void }) {
  return (
    <AdminShell
      title="Super Admin"
      actions={
        <button
          type="button"
          onClick={onLogout}
          style={{
            width: '100%',
            border: '1px solid rgba(255,255,255,0.28)',
            borderRadius: 999,
            padding: '10px 14px',
            background: 'transparent',
            color: '#fff',
            cursor: 'pointer'
          }}
        >
          退出登录
        </button>
      }
      nav={[
        { to: '/', label: '平台概览' },
        { to: '/tenants', label: '租户' },
        { to: '/plans', label: '套餐' },
        { to: '/audit', label: '审计' }
      ]}
    >
      <Routes>
        <Route path="/" element={<Overview session={session} />} />
        <Route path="/tenants" element={<TenantsPage session={session} />} />
        <Route path="/plans" element={<PlansPage session={session} />} />
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

export function App() {
  return (
    <I18nProvider>
      <BrowserRouter>
        <AppContent />
      </BrowserRouter>
    </I18nProvider>
  );
}
