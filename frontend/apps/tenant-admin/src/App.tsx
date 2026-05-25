import type { FormEvent, ReactElement, ReactNode } from 'react';
import { useEffect, useState } from 'react';

import { AdminShell, SectionHeader, StatCard } from '@agentx/admin-ui';
import {
  ApiRequestError,
  type AuthSession,
  type ChatbotDetail,
  type ChatbotSummary,
  copyChatbot,
  deleteChatbot,
  getChatbot,
  type ConversationDetail,
  getTenantQuotaOverview,
  deleteConversation,
  deleteKnowledgeSource,
  exportFaqs,
  exportConversation,
  getConversation,
  getKnowledgeSource,
  importFaqs,
  listConversations,
  type ConversationSummary,
  type TenantQuotaOverview,
  createChatbot,
  createFaq,
  createWebKnowledgeSource,
  type CreateChatbotRequest,
  type CreateWebKnowledgeSourceRequest,
  type CreateFaqRequest,
  type FaqSummary,
  type KnowledgeSourceDetail,
  type KnowledgeSourceSummary,
  type ImportFaqResult,
  listChatbots,
  listFaqs,
  listKnowledgeSources,
  login,
  refreshKnowledgeSource,
  updateKnowledgeSourceStatus,
  retryKnowledgeSource,
  updateChatbot,
  updateChatbotAppearance,
  updateChatbotBehavior,
  updateChatbotStatus,
  updateConversationStatus,
  updateFaq,
  updateFaqStatus,
  updateFaqStatuses,
  uploadKnowledgeFile,
  type UpdateChatbotRequest,
  type UpdateChatbotAppearanceRequest,
  type UpdateChatbotBehaviorRequest,
  type UpdateFaqRequest
} from '@agentx/api-client';
import { I18nProvider, useI18n } from '@agentx/i18n';
import { BrowserRouter, Navigate, Route, Routes, useLocation, useNavigate } from 'react-router-dom';

const STORAGE_KEY = 'agentx.tenant-admin.session';

const errorMessages: Record<string, string> = {
  INVALID_CREDENTIALS: '邮箱或密码错误，请重新输入。',
  ACCOUNT_DISABLED: '当前账号已被禁用，请联系管理员。',
  ACCOUNT_LOCKED: '登录失败次数过多，账号已被临时锁定。',
  TENANT_DISABLED: '当前租户已被停用，暂时无法登录。'
};

type ChatbotFormState = CreateChatbotRequest;
type FaqFormState = CreateFaqRequest;

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

function isTenantAdmin(session: AuthSession | null) {
  return Boolean(session?.tenantId && session.roles.includes('TENANT_ADMIN'));
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
    resize: multiline ? ('vertical' as const) : undefined
  };
}

function emptyChatbotForm(tenantId: number): ChatbotFormState {
  return {
    tenantId,
    name: '',
    description: '',
    language: 'zh-CN',
    status: 'DRAFT'
  };
}

function emptyFaqForm(tenantId: number, chatbotId: number): FaqFormState {
  return {
    tenantId,
    chatbotId,
    language: 'zh-CN',
    question: '',
    alternateQuestions: [],
    answer: ''
  };
}

function normalizeLines(value: string) {
  return value
    .split('\n')
    .map((item) => item.trim())
    .filter(Boolean);
}

function statusColor(status: string) {
  if (status === 'ACTIVE') {
    return { color: '#166534', background: '#dcfce7', label: '启用中' };
  }

  if (status === 'DISABLED') {
    return { color: '#991b1b', background: '#fee2e2', label: '已停用' };
  }

  if (status === 'DELETED') {
    return { color: '#475569', background: '#e2e8f0', label: '已删除' };
  }

  return { color: '#92400e', background: '#fef3c7', label: '草稿' };
}

function Field({ label, children }: { label: string; children: ReactNode }) {
  return (
    <label style={{ display: 'grid', gap: 8 }}>
      <span style={{ fontWeight: 600, color: '#0f172a' }}>{label}</span>
      {children}
    </label>
  );
}

function Banner({ tone, children }: { tone: 'error' | 'notice'; children: string }) {
  return (
    <div
      role={tone === 'error' ? 'alert' : undefined}
      style={{
        borderRadius: 16,
        padding: 14,
        background: tone === 'error' ? '#fef2f2' : '#fff7ed',
        color: tone === 'error' ? '#b91c1c' : '#9a3412'
      }}
    >
      {children}
    </div>
  );
}

function FormCard({
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
  children: ReactNode;
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
              background: '#c2410c',
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
      const nextSession = await login({ email, password });

      if (!isTenantAdmin(nextSession)) {
        throw new Error('FORBIDDEN_ROLE');
      }

      onLogin(nextSession);
    } catch (error) {
      const message = error instanceof ApiRequestError ? (error.code ?? 'LOGIN_FAILED') : 'LOGIN_FAILED';
      setErrorMessage(
        message === 'FORBIDDEN_ROLE'
          ? '该账号没有租户管理后台访问权限。'
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
        background: 'linear-gradient(135deg, #fff7ed 0%, #ffedd5 45%, #fde68a 100%)',
        padding: 24,
        fontFamily: 'ui-sans-serif, system-ui, sans-serif'
      }}
    >
      <div
        style={{
          width: 'min(100%, 420px)',
          background: 'rgba(255,255,255,0.94)',
          borderRadius: 24,
          padding: 28,
          boxShadow: '0 30px 80px rgba(120, 53, 15, 0.18)'
        }}
      >
        <p style={{ margin: 0, color: '#9a3412', fontWeight: 700, letterSpacing: 1.2 }}>租户管理员</p>
        <h1 style={{ marginTop: 12, marginBottom: 8 }}>登录租户管理台</h1>
        <p style={{ marginTop: 0, marginBottom: 24, color: '#7c2d12' }}>使用租户管理员邮箱和密码进入后台。</p>
        <form onSubmit={handleSubmit} style={{ display: 'grid', gap: 16 }}>
          <Field label="邮箱">
            <input
              type="email"
              value={email}
              onChange={(event) => setEmail(event.target.value)}
              required
              placeholder="admin@tenant.local"
              style={{ ...inputStyle(), fontSize: 16 }}
            />
          </Field>
          <Field label="密码">
            <input
              type="password"
              value={password}
              onChange={(event) => setPassword(event.target.value)}
              required
              placeholder="请输入密码"
              style={{ ...inputStyle(), fontSize: 16 }}
            />
          </Field>
          {errorMessage ? <Banner tone="error">{errorMessage}</Banner> : null}
          <button
            type="submit"
            disabled={submitting}
            style={{
              border: 0,
              borderRadius: 999,
              padding: '14px 18px',
              background: '#c2410c',
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

function Dashboard({ session }: { session: AuthSession }) {
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
        <StatCard title="Chatbot 数量" value={`${chatbotUsed} / ${chatbotLimit || '-'}`} description={quotaOverview ? `套餐 ${quotaOverview.planName}，状态 ${quotaOverview.planStatus}` : '额度概览加载中或暂未配置套餐。'} />
        <StatCard title="消息用量" value={`${messageUsed} / ${messageLimit || '-'}`} description="消息数来自已落库对话消息。" />
        <StatCard title="会话总数" value={String(conversationUsed)} description="可在会话页继续查看详情、导出与删除。" />
      </div>
    </>
  );
}

function UsagePage({ session }: { session: AuthSession }) {
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
        <div style={{ display: 'grid', gridTemplateColumns: 'minmax(320px, 1fr) minmax(320px, 1fr)', gap: 20 }}>
          <section style={{ background: '#fff', borderRadius: 20, padding: 20, boxShadow: '0 18px 50px rgba(15, 23, 42, 0.08)', display: 'grid', gap: 12 }}>
            <h3 style={{ margin: 0 }}>套餐信息</h3>
            <div>套餐：{overview.planName}</div>
            <div>编码：{overview.planCode}</div>
            <div>状态：{overview.planStatus}</div>
          </section>
          <section style={{ background: '#fff', borderRadius: 20, padding: 20, boxShadow: '0 18px 50px rgba(15, 23, 42, 0.08)', display: 'grid', gap: 12 }}>
            <h3 style={{ margin: 0 }}>资源用量</h3>
            {Object.entries(overview.effectiveLimits).map(([key, limit]) => (
              <div key={key} style={{ display: 'flex', justifyContent: 'space-between', gap: 12 }}>
                <span>{key}</span>
                <span>
                  {overview.usage[key] ?? 0} / {limit}
                </span>
              </div>
            ))}
          </section>
        </div>
      )}
    </>
  );
}

function ChatbotsPage({ session }: { session: AuthSession }) {
  const tenantId = session.tenantId ?? 0;
  const token = session.accessToken;
  const [chatbots, setChatbots] = useState<ChatbotSummary[]>([]);
  const [selectedChatbotId, setSelectedChatbotId] = useState<number | null>(null);
  const [loading, setLoading] = useState(true);
  const [submitting, setSubmitting] = useState(false);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);
  const [notice, setNotice] = useState<string | null>(null);
  const [createOpen, setCreateOpen] = useState(false);
  const [editOpen, setEditOpen] = useState(false);
  const [chatbotDetail, setChatbotDetail] = useState<ChatbotDetail | null>(null);
  const [createForm, setCreateForm] = useState<ChatbotFormState>(emptyChatbotForm(tenantId));
  const [editForm, setEditForm] = useState<UpdateChatbotRequest>({
    name: '',
    description: '',
    language: 'zh-CN',
    status: 'DRAFT'
  });
  const [appearanceForm, setAppearanceForm] = useState<UpdateChatbotAppearanceRequest>({
    themeColor: '#2563eb',
    welcomeMessage: '',
    brandVisible: true,
    launcherPosition: 'right'
  });
  const [behaviorForm, setBehaviorForm] = useState<UpdateChatbotBehaviorRequest>({
    fallbackMessage: '',
    allowDirectModel: false,
    allowFeedback: true,
    allowHandoff: true
  });

  const selectedChatbot = chatbots.find((chatbot) => chatbot.id === selectedChatbotId) ?? null;

  useEffect(() => {
    if (!tenantId) {
      return;
    }

    setLoading(true);
    setErrorMessage(null);

    void listChatbots(token, tenantId)
      .then((nextChatbots) => {
        setChatbots(nextChatbots);
        setSelectedChatbotId((current) => current ?? nextChatbots[0]?.id ?? null);
      })
      .catch((error) => setErrorMessage(error instanceof Error ? error.message : 'Chatbot 列表加载失败。'))
      .finally(() => setLoading(false));
  }, [tenantId, token]);

  useEffect(() => {
    if (!selectedChatbot) {
      return;
    }

    setEditForm({
      name: selectedChatbot.name,
      description: selectedChatbot.description ?? '',
      language: selectedChatbot.language,
      status: selectedChatbot.status
    });

    void getChatbot(token, selectedChatbot.id)
      .then((detail) => {
        setChatbotDetail(detail);
        setAppearanceForm({
          themeColor: detail.themeColor,
          welcomeMessage: detail.welcomeMessage,
          brandVisible: detail.brandVisible,
          launcherPosition: detail.launcherPosition
        });
        setBehaviorForm({
          fallbackMessage: detail.fallbackMessage,
          allowDirectModel: detail.allowDirectModel,
          allowFeedback: detail.allowFeedback,
          allowHandoff: detail.allowHandoff
        });
      })
      .catch(() => setChatbotDetail(null));
  }, [selectedChatbot, token]);

  const refreshChatbots = async (preferredId?: number) => {
    const nextChatbots = await listChatbots(token, tenantId);
    setChatbots(nextChatbots);
    setSelectedChatbotId(preferredId ?? nextChatbots[0]?.id ?? null);
  };

  const handleCreate = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    setSubmitting(true);
    setErrorMessage(null);
    setNotice(null);

    try {
      const createdChatbot = await createChatbot(token, {
        ...createForm,
        name: createForm.name.trim(),
        description: createForm.description.trim(),
        language: createForm.language.trim()
      });
      setCreateForm(emptyChatbotForm(tenantId));
      setCreateOpen(false);
      setNotice(`Chatbot ${createdChatbot.name} 已创建。`);
      await refreshChatbots(createdChatbot.id);
    } catch (error) {
      const message =
        error instanceof ApiRequestError && error.code === 'CHATBOTS_LIMIT_REACHED'
          ? '当前套餐的 Chatbot 数量已达上限，请先调整套餐额度或停用/删除已有机器人。'
          : error instanceof Error
            ? error.message
            : 'Chatbot 创建失败。';
      setErrorMessage(message);
    } finally {
      setSubmitting(false);
    }
  };

  const handleEdit = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    if (!selectedChatbot) {
      return;
    }

    setSubmitting(true);
    setErrorMessage(null);
    setNotice(null);

    try {
      const updatedChatbot = await updateChatbot(token, selectedChatbot.id, {
        ...editForm,
        name: editForm.name.trim(),
        description: editForm.description.trim(),
        language: editForm.language.trim()
      });
      setChatbots((current) => current.map((chatbot) => (chatbot.id === updatedChatbot.id ? updatedChatbot : chatbot)));
      setEditOpen(false);
      setNotice(`Chatbot ${updatedChatbot.name} 已更新。`);
    } catch (error) {
      setErrorMessage(error instanceof Error ? error.message : 'Chatbot 更新失败。');
    } finally {
      setSubmitting(false);
    }
  };

  const handleStatusChange = async (status: ChatbotSummary['status']) => {
    if (!selectedChatbot) {
      return;
    }

    setSubmitting(true);
    setErrorMessage(null);
    setNotice(null);

    try {
      const updatedChatbot = await updateChatbotStatus(token, selectedChatbot.id, status);
      setChatbots((current) => current.map((chatbot) => (chatbot.id === updatedChatbot.id ? updatedChatbot : chatbot)));
      setNotice(`Chatbot ${updatedChatbot.name} 状态已更新为 ${statusColor(updatedChatbot.status).label}。`);
    } catch (error) {
      setErrorMessage(error instanceof Error ? error.message : 'Chatbot 状态更新失败。');
    } finally {
      setSubmitting(false);
    }
  };

  const handleAppearanceSave = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    if (!selectedChatbot) {
      return;
    }

    setSubmitting(true);
    setErrorMessage(null);
    setNotice(null);

    try {
      const detail = await updateChatbotAppearance(token, selectedChatbot.id, appearanceForm);
      setChatbotDetail(detail);
      setChatbots((current) => current.map((chatbot) => (chatbot.id === detail.id ? detail : chatbot)));
      setNotice('Chatbot 外观配置已更新。');
    } catch (error) {
      setErrorMessage(error instanceof Error ? error.message : 'Chatbot 外观配置更新失败。');
    } finally {
      setSubmitting(false);
    }
  };

  const handleCopy = async () => {
    if (!selectedChatbot) {
      return;
    }

    setSubmitting(true);
    setErrorMessage(null);
    setNotice(null);

    try {
      const copiedChatbot = await copyChatbot(token, selectedChatbot.id);
      await refreshChatbots(copiedChatbot.id);
      setNotice(`Chatbot ${selectedChatbot.name} 已复制。`);
    } catch (error) {
      setErrorMessage(error instanceof Error ? error.message : 'Chatbot 复制失败。');
    } finally {
      setSubmitting(false);
    }
  };

  const handleDelete = async () => {
    if (!selectedChatbot) {
      return;
    }

    const confirmed = window.confirm('删除后该 Chatbot 将被标记为 DELETED。是否继续？');

    if (!confirmed) {
      return;
    }

    setSubmitting(true);
    setErrorMessage(null);
    setNotice(null);

    try {
      const deletedChatbot = await deleteChatbot(token, selectedChatbot.id);
      setChatbotDetail((current) => (current?.id === deletedChatbot.id ? { ...current, status: deletedChatbot.status } : current));
      setChatbots((current) => current.map((chatbot) => (chatbot.id === deletedChatbot.id ? { ...chatbot, status: deletedChatbot.status } : chatbot)));
      setNotice(`Chatbot ${deletedChatbot.name} 已标记为 DELETED。`);
    } catch (error) {
      setErrorMessage(error instanceof Error ? error.message : 'Chatbot 删除失败。');
    } finally {
      setSubmitting(false);
    }
  };

  const openPreview = (surface: 'chat-page' | 'widget') => {
    if (!selectedChatbot || typeof window === 'undefined') {
      return;
    }

    const baseUrl = surface === 'chat-page' ? 'http://localhost:4173' : 'http://localhost:4174';
    window.open(`${baseUrl}?bot=${encodeURIComponent(selectedChatbot.publicCode)}`, '_blank', 'noopener,noreferrer');
  };

  const handleBehaviorSave = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    if (!selectedChatbot) {
      return;
    }

    setSubmitting(true);
    setErrorMessage(null);
    setNotice(null);

    try {
      const detail = await updateChatbotBehavior(token, selectedChatbot.id, behaviorForm);
      setChatbotDetail(detail);
      setChatbots((current) => current.map((chatbot) => (chatbot.id === detail.id ? detail : chatbot)));
      setNotice('Chatbot 行为策略已更新。');
    } catch (error) {
      setErrorMessage(error instanceof Error ? error.message : 'Chatbot 行为策略更新失败。');
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <>
      <SectionHeader
        title="Chatbot 管理"
        actions={
          <button
            type="button"
            onClick={() => {
              setCreateOpen((current) => !current);
              setEditOpen(false);
            }}
            style={{ borderRadius: 999, border: 0, background: '#c2410c', color: '#fff', padding: '10px 16px', cursor: 'pointer' }}
          >
            {createOpen ? '收起新建' : '新建 Chatbot'}
          </button>
        }
      />

      <div style={{ display: 'grid', gap: 16 }}>
        {errorMessage ? <Banner tone="error">{errorMessage}</Banner> : null}
        {notice ? <Banner tone="notice">{notice}</Banner> : null}
      </div>

      <div style={{ display: 'grid', gridTemplateColumns: 'minmax(360px, 1fr) minmax(360px, 1fr)', gap: 20, marginTop: 16 }}>
        <section style={{ display: 'grid', gap: 16 }}>
          <div style={{ background: '#fff', borderRadius: 20, boxShadow: '0 18px 50px rgba(15, 23, 42, 0.08)', overflow: 'hidden' }}>
            <div style={{ padding: 18, borderBottom: '1px solid #e2e8f0' }}>
              <strong>机器人列表</strong>
              <p style={{ marginBottom: 0, color: '#64748b' }}>查看名称、语言、状态和公共访问码。</p>
            </div>
            {loading ? (
              <div style={{ padding: 18, color: '#64748b' }}>加载中...</div>
            ) : chatbots.length === 0 ? (
              <div style={{ padding: 18, color: '#64748b' }}>还没有 Chatbot，先创建一个。</div>
            ) : (
              <div style={{ display: 'grid' }}>
                {chatbots.map((chatbot) => {
                  const active = chatbot.id === selectedChatbotId;
                  const badge = statusColor(chatbot.status);

                  return (
                    <button
                      key={chatbot.id}
                      type="button"
                      onClick={() => {
                        setSelectedChatbotId(chatbot.id);
                        setEditOpen(false);
                      }}
                      style={{
                        display: 'grid',
                        gap: 8,
                        textAlign: 'left',
                        padding: 18,
                        border: 0,
                        borderBottom: '1px solid #e2e8f0',
                        background: active ? '#fff7ed' : '#fff',
                        cursor: 'pointer'
                      }}
                    >
                      <div style={{ display: 'flex', justifyContent: 'space-between', gap: 12 }}>
                        <div>
                          <strong>{chatbot.name}</strong>
                          <div style={{ color: '#64748b', fontSize: 13 }}>{chatbot.language}</div>
                        </div>
                        <span style={{ borderRadius: 999, padding: '4px 10px', fontSize: 12, fontWeight: 700, color: badge.color, background: badge.background }}>
                          {badge.label}
                        </span>
                      </div>
                      <div style={{ color: '#334155', fontSize: 14 }}>
                        公开码：{chatbot.publicCode}
                        <br />
                        主题色：{chatbot.themeColor}
                      </div>
                    </button>
                  );
                })}
              </div>
            )}
          </div>

          {createOpen ? (
            <FormCard title="创建 Chatbot" description="配置基础信息后即可在公开页面或组件中使用。" submitLabel="创建 Chatbot" submitting={submitting} onSubmit={handleCreate} onCancel={() => setCreateOpen(false)}>
              <Field label="名称">
                <input value={createForm.name} onChange={(event) => setCreateForm((current) => ({ ...current, name: event.target.value }))} required style={inputStyle()} />
              </Field>
              <Field label="描述">
                <textarea value={createForm.description} onChange={(event) => setCreateForm((current) => ({ ...current, description: event.target.value }))} style={inputStyle(true)} />
              </Field>
              <Field label="语言">
                <input value={createForm.language} onChange={(event) => setCreateForm((current) => ({ ...current, language: event.target.value }))} required style={inputStyle()} />
              </Field>
              <Field label="状态">
                <select value={createForm.status} onChange={(event) => setCreateForm((current) => ({ ...current, status: event.target.value as ChatbotSummary['status'] }))} style={inputStyle()}>
                  <option value="DRAFT">草稿</option>
                  <option value="ACTIVE">启用</option>
                  <option value="DISABLED">停用</option>
                </select>
              </Field>
            </FormCard>
          ) : null}
        </section>

        <section style={{ display: 'grid', gap: 16 }}>
          <div style={{ background: '#fff', borderRadius: 20, padding: 20, boxShadow: '0 18px 50px rgba(15, 23, 42, 0.08)', display: 'grid', gap: 16 }}>
            <div style={{ display: 'flex', justifyContent: 'space-between', gap: 12, alignItems: 'center' }}>
              <div>
                <h3 style={{ margin: 0 }}>Chatbot 详情</h3>
                <p style={{ marginBottom: 0, color: '#64748b' }}>查看默认欢迎语、兜底话术和公开访问码。</p>
              </div>
              {selectedChatbot ? (
                <button type="button" onClick={() => setEditOpen((current) => !current)} style={{ borderRadius: 999, border: '1px solid #cbd5e1', background: '#fff', padding: '10px 14px', cursor: 'pointer' }}>
                  {editOpen ? '收起编辑' : '编辑'}
                </button>
              ) : null}
            </div>

            {!selectedChatbot ? (
              <div style={{ color: '#64748b' }}>从左侧选择一个 Chatbot 查看详情。</div>
            ) : (
              <>
                <div style={{ display: 'grid', gap: 10, color: '#334155' }}>
                  <div>名称：{selectedChatbot.name}</div>
                  <div>描述：{selectedChatbot.description || '暂无描述'}</div>
                  <div>语言：{selectedChatbot.language}</div>
                  <div>公开码：{selectedChatbot.publicCode}</div>
                  <div>欢迎语：{chatbotDetail?.welcomeMessage ?? selectedChatbot.welcomeMessage}</div>
                  <div>兜底话术：{chatbotDetail?.fallbackMessage ?? selectedChatbot.fallbackMessage}</div>
                  <div>品牌标识：{chatbotDetail?.brandVisible ? '显示' : '隐藏'}</div>
                  <div>启动按钮位置：{chatbotDetail?.launcherPosition ?? 'right'}</div>
                </div>
                <div style={{ display: 'flex', gap: 12, flexWrap: 'wrap' }}>
                  <button type="button" disabled={submitting} onClick={() => void handleStatusChange('ACTIVE')} style={{ borderRadius: 999, border: 0, background: '#166534', color: '#fff', padding: '10px 16px', cursor: 'pointer' }}>
                    启用
                  </button>
                  <button type="button" disabled={submitting} onClick={() => void handleStatusChange('DISABLED')} style={{ borderRadius: 999, border: 0, background: '#b91c1c', color: '#fff', padding: '10px 16px', cursor: 'pointer' }}>
                    停用
                  </button>
                  <button type="button" disabled={submitting} onClick={() => openPreview('chat-page')} style={{ borderRadius: 999, border: '1px solid #cbd5e1', background: '#fff', color: '#0f172a', padding: '10px 16px', cursor: 'pointer' }}>
                    预览聊天页
                  </button>
                  <button type="button" disabled={submitting} onClick={() => openPreview('widget')} style={{ borderRadius: 999, border: '1px solid #cbd5e1', background: '#fff', color: '#0f172a', padding: '10px 16px', cursor: 'pointer' }}>
                    预览 Widget
                  </button>
                  <button type="button" disabled={submitting} onClick={() => void handleCopy()} style={{ borderRadius: 999, border: '1px solid #cbd5e1', background: '#fff', color: '#0f172a', padding: '10px 16px', cursor: 'pointer' }}>
                    复制
                  </button>
                  <button type="button" disabled={submitting} onClick={() => void handleDelete()} style={{ borderRadius: 999, border: '1px solid #fecaca', background: '#fff', color: '#991b1b', padding: '10px 16px', cursor: 'pointer' }}>
                    删除
                  </button>
                </div>
              </>
            )}
          </div>

          {selectedChatbot ? (
            <FormCard title="外观配置" description="调整主题色、欢迎语和品牌展示。" submitLabel="保存外观" submitting={submitting} onSubmit={handleAppearanceSave} onCancel={() => setAppearanceForm({ themeColor: chatbotDetail?.themeColor ?? selectedChatbot.themeColor, welcomeMessage: chatbotDetail?.welcomeMessage ?? selectedChatbot.welcomeMessage, brandVisible: chatbotDetail?.brandVisible ?? true, launcherPosition: chatbotDetail?.launcherPosition ?? 'right' })}>
              <Field label="主题色">
                <input value={appearanceForm.themeColor} onChange={(event) => setAppearanceForm((current) => ({ ...current, themeColor: event.target.value }))} style={inputStyle()} />
              </Field>
              <Field label="欢迎语">
                <textarea value={appearanceForm.welcomeMessage} onChange={(event) => setAppearanceForm((current) => ({ ...current, welcomeMessage: event.target.value }))} style={inputStyle(true)} />
              </Field>
              <Field label="品牌标识">
                <select value={appearanceForm.brandVisible ? 'true' : 'false'} onChange={(event) => setAppearanceForm((current) => ({ ...current, brandVisible: event.target.value === 'true' }))} style={inputStyle()}>
                  <option value="true">显示</option>
                  <option value="false">隐藏</option>
                </select>
              </Field>
              <Field label="启动按钮位置">
                <select value={appearanceForm.launcherPosition} onChange={(event) => setAppearanceForm((current) => ({ ...current, launcherPosition: event.target.value }))} style={inputStyle()}>
                  <option value="right">右侧</option>
                  <option value="left">左侧</option>
                </select>
              </Field>
            </FormCard>
          ) : null}

          {selectedChatbot ? (
            <FormCard title="行为策略" description="调整兜底回复、模型直连、反馈和转人工开关。" submitLabel="保存策略" submitting={submitting} onSubmit={handleBehaviorSave} onCancel={() => setBehaviorForm({ fallbackMessage: chatbotDetail?.fallbackMessage ?? selectedChatbot.fallbackMessage, allowDirectModel: chatbotDetail?.allowDirectModel ?? false, allowFeedback: chatbotDetail?.allowFeedback ?? true, allowHandoff: chatbotDetail?.allowHandoff ?? true })}>
              <Field label="兜底话术">
                <textarea value={behaviorForm.fallbackMessage} onChange={(event) => setBehaviorForm((current) => ({ ...current, fallbackMessage: event.target.value }))} style={inputStyle(true)} />
              </Field>
              <Field label="允许模型直连">
                <select value={behaviorForm.allowDirectModel ? 'true' : 'false'} onChange={(event) => setBehaviorForm((current) => ({ ...current, allowDirectModel: event.target.value === 'true' }))} style={inputStyle()}>
                  <option value="false">关闭</option>
                  <option value="true">开启</option>
                </select>
              </Field>
              <Field label="允许反馈">
                <select value={behaviorForm.allowFeedback ? 'true' : 'false'} onChange={(event) => setBehaviorForm((current) => ({ ...current, allowFeedback: event.target.value === 'true' }))} style={inputStyle()}>
                  <option value="true">开启</option>
                  <option value="false">关闭</option>
                </select>
              </Field>
              <Field label="允许转人工">
                <select value={behaviorForm.allowHandoff ? 'true' : 'false'} onChange={(event) => setBehaviorForm((current) => ({ ...current, allowHandoff: event.target.value === 'true' }))} style={inputStyle()}>
                  <option value="true">开启</option>
                  <option value="false">关闭</option>
                </select>
              </Field>
            </FormCard>
          ) : null}

          {editOpen && selectedChatbot ? (
            <FormCard title="编辑 Chatbot" description="更新基础信息和上线状态。" submitLabel="保存修改" submitting={submitting} onSubmit={handleEdit} onCancel={() => setEditOpen(false)}>
              <Field label="名称">
                <input value={editForm.name} onChange={(event) => setEditForm((current) => ({ ...current, name: event.target.value }))} required style={inputStyle()} />
              </Field>
              <Field label="描述">
                <textarea value={editForm.description} onChange={(event) => setEditForm((current) => ({ ...current, description: event.target.value }))} style={inputStyle(true)} />
              </Field>
              <Field label="语言">
                <input value={editForm.language} onChange={(event) => setEditForm((current) => ({ ...current, language: event.target.value }))} required style={inputStyle()} />
              </Field>
              <Field label="状态">
                <select value={editForm.status} onChange={(event) => setEditForm((current) => ({ ...current, status: event.target.value as ChatbotSummary['status'] }))} style={inputStyle()}>
                  <option value="DRAFT">草稿</option>
                  <option value="ACTIVE">启用</option>
                  <option value="DISABLED">停用</option>
                </select>
              </Field>
            </FormCard>
          ) : null}
        </section>
      </div>
    </>
  );
}

function KnowledgePage({ session }: { session: AuthSession }) {
  const tenantId = session.tenantId ?? 0;
  const token = session.accessToken;
  const [chatbots, setChatbots] = useState<ChatbotSummary[]>([]);
  const [selectedChatbotId, setSelectedChatbotId] = useState<number | null>(null);
  const [languageFilter, setLanguageFilter] = useState('');
  const [keywordFilter, setKeywordFilter] = useState('');
  const [statusFilter, setStatusFilter] = useState<FaqSummary['status'] | ''>('');
  const [knowledgeSources, setKnowledgeSources] = useState<KnowledgeSourceSummary[]>([]);
  const [selectedKnowledgeSourceId, setSelectedKnowledgeSourceId] = useState<number | null>(null);
  const [selectedKnowledgeSource, setSelectedKnowledgeSource] = useState<KnowledgeSourceDetail | null>(null);
  const [selectedKnowledgeFile, setSelectedKnowledgeFile] = useState<File | null>(null);
  const [webSourceForm, setWebSourceForm] = useState<CreateWebKnowledgeSourceRequest>({
    name: '',
    url: ''
  });
  const [faqs, setFaqs] = useState<FaqSummary[]>([]);
  const [selectedFaqId, setSelectedFaqId] = useState<number | null>(null);
  const [selectedFaqIds, setSelectedFaqIds] = useState<number[]>([]);
  const [loading, setLoading] = useState(true);
  const [submitting, setSubmitting] = useState(false);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);
  const [notice, setNotice] = useState<string | null>(null);
  const [createOpen, setCreateOpen] = useState(false);
  const [editOpen, setEditOpen] = useState(false);
  const [importOpen, setImportOpen] = useState(false);
  const [importPayload, setImportPayload] = useState('');
  const [importResult, setImportResult] = useState<ImportFaqResult | null>(null);
  const [createForm, setCreateForm] = useState<FaqFormState>(emptyFaqForm(tenantId, 0));
  const [editForm, setEditForm] = useState<UpdateFaqRequest>({
    language: 'zh-CN',
    question: '',
    alternateQuestions: [],
    answer: ''
  });

  const selectedFaq = faqs.find((faq) => faq.id === selectedFaqId) ?? null;

  const loadFaqs = async (chatbotId: number, preserveSelection = true) => {
    const nextFaqs = await listFaqs(token, {
      tenantId,
      chatbotId,
      language: languageFilter || undefined,
      keyword: keywordFilter || undefined,
      status: statusFilter || undefined
    });

    setFaqs(nextFaqs);
    setSelectedFaqId((current) => {
      if (preserveSelection && current && nextFaqs.some((faq) => faq.id === current)) {
        return current;
      }

      return nextFaqs[0]?.id ?? null;
    });
    setSelectedFaqIds((current) => current.filter((faqId) => nextFaqs.some((faq) => faq.id === faqId)));
    setCreateForm(emptyFaqForm(tenantId, chatbotId));
  };

  const loadKnowledgeSources = async (chatbotId: number) => {
    const nextSources = await listKnowledgeSources(token, tenantId, chatbotId);
    setKnowledgeSources(nextSources);
    setSelectedKnowledgeSourceId((current) =>
      current && nextSources.some((source) => source.id === current) ? current : (nextSources[0]?.id ?? null)
    );
  };

  const loadKnowledgeSourceDetail = async (chatbotId: number, sourceId: number) => {
    const nextSource = await getKnowledgeSource(token, tenantId, chatbotId, sourceId);
    setSelectedKnowledgeSource(nextSource);
  };

  useEffect(() => {
    if (!tenantId) {
      return;
    }

    void listChatbots(token, tenantId)
      .then((nextChatbots) => {
        setChatbots(nextChatbots);
        setSelectedChatbotId(nextChatbots[0]?.id ?? null);
      })
      .catch((error) => setErrorMessage(error instanceof Error ? error.message : 'Chatbot 列表加载失败。'));
  }, [tenantId, token]);

  useEffect(() => {
    if (!selectedChatbotId) {
      setFaqs([]);
      setKnowledgeSources([]);
      setSelectedKnowledgeSourceId(null);
      setSelectedKnowledgeSource(null);
      setSelectedFaqId(null);
      setSelectedFaqIds([]);
      setLoading(false);
      return;
    }

    setLoading(true);
    setErrorMessage(null);

    void Promise.all([loadFaqs(selectedChatbotId), loadKnowledgeSources(selectedChatbotId)])
      .catch((error) => setErrorMessage(error instanceof Error ? error.message : 'FAQ 列表加载失败。'))
      .finally(() => setLoading(false));
  }, [selectedChatbotId, tenantId, token, languageFilter, keywordFilter, statusFilter]);

  useEffect(() => {
    if (!selectedChatbotId || !selectedKnowledgeSourceId) {
      setSelectedKnowledgeSource(null);
      return;
    }

    void loadKnowledgeSourceDetail(selectedChatbotId, selectedKnowledgeSourceId).catch((error) =>
      setErrorMessage(error instanceof Error ? error.message : '知识来源详情加载失败。')
    );
  }, [selectedChatbotId, selectedKnowledgeSourceId, tenantId, token]);

  useEffect(() => {
    if (!selectedFaq) {
      return;
    }

    setEditForm({
      language: selectedFaq.language,
      question: selectedFaq.question,
      alternateQuestions: selectedFaq.alternateQuestions,
      answer: selectedFaq.answer
    });
  }, [selectedFaq]);

  const handleCreate = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    if (!selectedChatbotId) {
      return;
    }

    setSubmitting(true);
    setErrorMessage(null);
    setNotice(null);
    setImportResult(null);

    try {
      const createdFaq = await createFaq(token, {
        ...createForm,
        chatbotId: selectedChatbotId,
        language: createForm.language.trim(),
        question: createForm.question.trim(),
        alternateQuestions: createForm.alternateQuestions,
        answer: createForm.answer.trim()
      });
      await loadFaqs(selectedChatbotId, false);
      setSelectedFaqId(createdFaq.id);
      setCreateOpen(false);
      setNotice('FAQ 已创建。');
    } catch (error) {
      setErrorMessage(error instanceof Error ? error.message : 'FAQ 创建失败。');
    } finally {
      setSubmitting(false);
    }
  };

  const handleEdit = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    if (!selectedFaq) {
      return;
    }

    setSubmitting(true);
    setErrorMessage(null);
    setNotice(null);
    setImportResult(null);

    try {
      const updatedFaq = await updateFaq(token, selectedFaq.id, {
        language: editForm.language.trim(),
        question: editForm.question.trim(),
        alternateQuestions: editForm.alternateQuestions,
        answer: editForm.answer.trim()
      });
      setFaqs((current) => current.map((faq) => (faq.id === updatedFaq.id ? updatedFaq : faq)));
      setSelectedFaqIds((current) => (current.includes(updatedFaq.id) ? current : current));
      setEditOpen(false);
      setNotice('FAQ 已更新。');
    } catch (error) {
      setErrorMessage(error instanceof Error ? error.message : 'FAQ 更新失败。');
    } finally {
      setSubmitting(false);
    }
  };

  const handleStatusChange = async (status: FaqSummary['status']) => {
    if (!selectedFaq) {
      return;
    }

    setSubmitting(true);
    setErrorMessage(null);
    setNotice(null);
    setImportResult(null);

    try {
      const updatedFaq = await updateFaqStatus(token, selectedFaq.id, status);
      setFaqs((current) => current.map((faq) => (faq.id === updatedFaq.id ? updatedFaq : faq)));
      setNotice(`FAQ 状态已更新为 ${statusColor(updatedFaq.status).label}。`);
    } catch (error) {
      setErrorMessage(error instanceof Error ? error.message : 'FAQ 状态更新失败。');
    } finally {
      setSubmitting(false);
    }
  };

  const handleBulkStatusChange = async (status: FaqSummary['status']) => {
    if (selectedFaqIds.length === 0) {
      return;
    }

    setSubmitting(true);
    setErrorMessage(null);
    setNotice(null);

    try {
      const updatedFaqs = await updateFaqStatuses(token, selectedFaqIds, status);
      const updatedMap = new Map(updatedFaqs.map((faq) => [faq.id, faq]));
      setFaqs((current) => current.map((faq) => updatedMap.get(faq.id) ?? faq));
      setNotice(`已批量更新 ${updatedFaqs.length} 条 FAQ 状态为 ${statusColor(status).label}。`);
    } catch (error) {
      setErrorMessage(error instanceof Error ? error.message : 'FAQ 批量状态更新失败。');
    } finally {
      setSubmitting(false);
    }
  };

  const handleExport = async () => {
    if (!selectedChatbotId) {
      return;
    }

    setSubmitting(true);
    setErrorMessage(null);
    setNotice(null);

    try {
      const exportBlob = await exportFaqs(token, tenantId, selectedChatbotId);
      const objectUrl = window.URL.createObjectURL(exportBlob);
      const anchor = document.createElement('a');
      anchor.href = objectUrl;
      anchor.download = `faq-${tenantId}-${selectedChatbotId}.json`;
      anchor.click();
      window.URL.revokeObjectURL(objectUrl);
      setNotice(`FAQ 已导出，Chatbot ID ${selectedChatbotId}。`);
    } catch (error) {
      setErrorMessage(error instanceof Error ? error.message : 'FAQ 导出失败。');
    } finally {
      setSubmitting(false);
    }
  };

  const handleImport = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    if (!selectedChatbotId) {
      return;
    }

    setSubmitting(true);
    setErrorMessage(null);
    setNotice(null);
    setImportResult(null);

    try {
      const parsed = JSON.parse(importPayload) as { items?: Array<{ language?: string; status?: FaqSummary['status']; question?: string; alternateQuestions?: string[]; answer?: string }> } | Array<{ language?: string; status?: FaqSummary['status']; question?: string; alternateQuestions?: string[]; answer?: string }>;
      const items = Array.isArray(parsed) ? parsed : parsed.items ?? [];
      const result = await importFaqs(
        token,
        tenantId,
        selectedChatbotId,
        items.map((item) => ({
          language: item.language ?? 'zh-CN',
          status: item.status,
          question: item.question ?? '',
          alternateQuestions: item.alternateQuestions ?? [],
          answer: item.answer ?? ''
        }))
      );
      setImportResult(result);
      await loadFaqs(selectedChatbotId, false);
      setNotice(`FAQ 导入完成，成功 ${result.importedCount} 条。`);
    } catch (error) {
      setErrorMessage(error instanceof Error ? error.message : 'FAQ 导入失败。');
    } finally {
      setSubmitting(false);
    }
  };

  const toggleFaqSelection = (faqId: number, checked: boolean) => {
    setSelectedFaqIds((current) => {
      if (checked) {
        return current.includes(faqId) ? current : [...current, faqId];
      }

      return current.filter((item) => item !== faqId);
    });
  };

  const handleKnowledgeUpload = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    if (!selectedChatbotId || !selectedKnowledgeFile) {
      return;
    }

    setSubmitting(true);
    setErrorMessage(null);
    setNotice(null);

    try {
      const uploadedSource = await uploadKnowledgeFile(token, tenantId, selectedChatbotId, selectedKnowledgeFile);
      await loadKnowledgeSources(selectedChatbotId);
      setSelectedKnowledgeFile(null);
      setSelectedKnowledgeSourceId(uploadedSource.id);
      setNotice(`知识文件 ${uploadedSource.sourceName} 已上传。`);
    } catch (error) {
      const message =
        error instanceof ApiRequestError && error.code === 'FILES_LIMIT_REACHED'
          ? '当前套餐的文件数量已达上限，请先清理或扩容。'
          : error instanceof ApiRequestError && error.code === 'STORAGE_MB_LIMIT_REACHED'
            ? '当前套餐的知识库存储额度已达上限，请先清理或扩容。'
            : error instanceof ApiRequestError && error.code === 'FILE_TYPE_NOT_SUPPORTED'
              ? '文件类型不支持，目前仅支持 txt、md、pdf、docx、csv、json。'
              : error instanceof ApiRequestError && error.code === 'FILE_SIZE_LIMIT_EXCEEDED'
                ? '文件大小超出限制，单个文件最大支持 10 MB。'
            : error instanceof Error
              ? error.message
              : '知识文件上传失败。';
      setErrorMessage(message);
    } finally {
      setSubmitting(false);
    }
  };

  const handleWebSourceCreate = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    if (!selectedChatbotId) {
      return;
    }

    setSubmitting(true);
    setErrorMessage(null);
    setNotice(null);

    try {
      const createdSource = await createWebKnowledgeSource(token, tenantId, selectedChatbotId, {
        name: webSourceForm.name.trim(),
        url: webSourceForm.url.trim()
      });
      await loadKnowledgeSources(selectedChatbotId);
      setWebSourceForm({ name: '', url: '' });
      setSelectedKnowledgeSourceId(createdSource.id);
      setNotice(`网页知识源 ${createdSource.sourceName} 已创建。`);
    } catch (error) {
      const message =
        error instanceof ApiRequestError && error.code === 'INVALID_SOURCE_URL'
          ? '网页地址无效，目前仅支持 http 或 https 链接。'
          : error instanceof ApiRequestError && error.code === 'URL_REQUIRED'
            ? '请输入网页地址。'
            : error instanceof Error
              ? error.message
              : '网页知识源创建失败。';
      setErrorMessage(message);
    } finally {
      setSubmitting(false);
    }
  };

  const handleKnowledgeRefresh = async () => {
    if (!selectedChatbotId || !selectedKnowledgeSourceId) {
      return;
    }

    setSubmitting(true);
    setErrorMessage(null);
    setNotice(null);

    try {
      const refreshedSource = await refreshKnowledgeSource(token, tenantId, selectedChatbotId, selectedKnowledgeSourceId);
      await loadKnowledgeSources(selectedChatbotId);
      setSelectedKnowledgeSource(refreshedSource);
      setNotice(`知识来源 ${refreshedSource.sourceName} 已刷新。`);
    } catch (error) {
      setErrorMessage(error instanceof Error ? error.message : '知识来源刷新失败。');
    } finally {
      setSubmitting(false);
    }
  };

  const handleKnowledgeRetry = async () => {
    if (!selectedChatbotId || !selectedKnowledgeSourceId) {
      return;
    }

    setSubmitting(true);
    setErrorMessage(null);
    setNotice(null);

    try {
      const retriedSource = await retryKnowledgeSource(token, tenantId, selectedChatbotId, selectedKnowledgeSourceId);
      await loadKnowledgeSources(selectedChatbotId);
      setSelectedKnowledgeSource(retriedSource);
      setNotice(`知识来源 ${retriedSource.sourceName} 已重试。`);
    } catch (error) {
      setErrorMessage(error instanceof Error ? error.message : '知识来源重试失败。');
    } finally {
      setSubmitting(false);
    }
  };

  const handleKnowledgeDisable = async () => {
    if (!selectedChatbotId || !selectedKnowledgeSourceId) {
      return;
    }

    setSubmitting(true);
    setErrorMessage(null);
    setNotice(null);

    try {
      const updatedSource = await updateKnowledgeSourceStatus(token, tenantId, selectedChatbotId, selectedKnowledgeSourceId, 'DISABLED');
      await loadKnowledgeSources(selectedChatbotId);
      setSelectedKnowledgeSource(updatedSource);
      setNotice(`知识来源 ${updatedSource.sourceName} 已停用。`);
    } catch (error) {
      setErrorMessage(error instanceof Error ? error.message : '知识来源停用失败。');
    } finally {
      setSubmitting(false);
    }
  };

  const handleKnowledgeEnable = async () => {
    if (!selectedChatbotId || !selectedKnowledgeSourceId) {
      return;
    }

    setSubmitting(true);
    setErrorMessage(null);
    setNotice(null);

    try {
      const updatedSource = await updateKnowledgeSourceStatus(token, tenantId, selectedChatbotId, selectedKnowledgeSourceId, 'ACTIVE');
      await loadKnowledgeSources(selectedChatbotId);
      setSelectedKnowledgeSource(updatedSource);
      setNotice(`知识来源 ${updatedSource.sourceName} 已启用。`);
    } catch (error) {
      setErrorMessage(error instanceof Error ? error.message : '知识来源启用失败。');
    } finally {
      setSubmitting(false);
    }
  };

  const handleKnowledgeDelete = async () => {
    if (!selectedChatbotId || !selectedKnowledgeSourceId) {
      return;
    }

    const confirmed = window.confirm('删除后该知识来源将被标记为 DELETED，并释放已生成的 chunk。是否继续？');
    if (!confirmed) {
      return;
    }

    setSubmitting(true);
    setErrorMessage(null);
    setNotice(null);

    try {
      const deletedSource = await deleteKnowledgeSource(token, tenantId, selectedChatbotId, selectedKnowledgeSourceId);
      await loadKnowledgeSources(selectedChatbotId);
      setNotice(`知识来源 ${deletedSource.sourceName} 已删除。`);
    } catch (error) {
      setErrorMessage(error instanceof Error ? error.message : '知识来源删除失败。');
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <>
      <SectionHeader
        title="FAQ 管理"
        actions={
          <div style={{ display: 'flex', gap: 12, flexWrap: 'wrap' }}>
            <button
              type="button"
              onClick={() => {
                setImportOpen((current) => !current);
                setCreateOpen(false);
                setEditOpen(false);
              }}
              disabled={!selectedChatbotId}
              style={{ borderRadius: 999, border: '1px solid #cbd5e1', background: '#fff', color: '#0f172a', padding: '10px 16px', cursor: 'pointer', opacity: selectedChatbotId ? 1 : 0.5 }}
            >
              {importOpen ? '收起导入' : '导入 FAQ'}
            </button>
            <button
              type="button"
              onClick={() => void handleExport()}
              disabled={!selectedChatbotId || submitting}
              style={{ borderRadius: 999, border: '1px solid #cbd5e1', background: '#fff', color: '#0f172a', padding: '10px 16px', cursor: 'pointer', opacity: selectedChatbotId ? 1 : 0.5 }}
            >
              导出 FAQ
            </button>
            <button
              type="button"
              onClick={() => {
                setCreateOpen((current) => !current);
                setEditOpen(false);
              }}
              disabled={!selectedChatbotId}
              style={{
                borderRadius: 999,
                border: 0,
                background: '#c2410c',
                color: '#fff',
                padding: '10px 16px',
                cursor: 'pointer',
                opacity: selectedChatbotId ? 1 : 0.5
              }}
            >
              {createOpen ? '收起新建' : '新建 FAQ'}
            </button>
          </div>
        }
      />

      <div style={{ display: 'grid', gap: 16 }}>
        {errorMessage ? <Banner tone="error">{errorMessage}</Banner> : null}
        {notice ? <Banner tone="notice">{notice}</Banner> : null}
      </div>

      <div style={{ display: 'grid', gridTemplateColumns: 'repeat(4, minmax(0, 1fr))', gap: 16, marginTop: 16, marginBottom: 16 }}>
        <Field label="所属 Chatbot">
          <select value={selectedChatbotId ?? ''} onChange={(event) => setSelectedChatbotId(event.target.value ? Number(event.target.value) : null)} style={inputStyle()}>
            <option value="">请选择 Chatbot</option>
            {chatbots.map((chatbot) => (
              <option key={chatbot.id} value={chatbot.id}>
                {chatbot.name}
              </option>
            ))}
          </select>
        </Field>
        <Field label="语言筛选">
          <input value={languageFilter} onChange={(event) => setLanguageFilter(event.target.value)} placeholder="如 zh-CN" style={inputStyle()} />
        </Field>
        <Field label="关键词">
          <input value={keywordFilter} onChange={(event) => setKeywordFilter(event.target.value)} placeholder="搜索问题或答案" style={inputStyle()} />
        </Field>
        <Field label="状态筛选">
          <select value={statusFilter} onChange={(event) => setStatusFilter(event.target.value as FaqSummary['status'] | '')} style={inputStyle()}>
            <option value="">全部状态</option>
            <option value="ACTIVE">启用</option>
            <option value="DISABLED">停用</option>
            <option value="DELETED">删除</option>
          </select>
        </Field>
      </div>

      {importOpen ? (
        <div style={{ marginBottom: 16 }}>
          <FormCard title="导入 FAQ" description="粘贴 FAQ 导出 JSON，或仅提供 items 数组。系统会返回逐条失败原因。" submitLabel="开始导入" submitting={submitting} onSubmit={handleImport} onCancel={() => setImportOpen(false)}>
            <Field label="JSON 内容">
              <textarea value={importPayload} onChange={(event) => setImportPayload(event.target.value)} style={inputStyle(true)} placeholder='{"items":[{"language":"zh-CN","question":"...","alternateQuestions":[],"answer":"..."}]}' />
            </Field>
            {importResult ? (
              <div style={{ display: 'grid', gap: 8, color: '#334155' }}>
                <div>成功导入：{importResult.importedCount}</div>
                <div>失败条数：{importResult.failures.length}</div>
                {importResult.failures.length > 0 ? (
                  <div style={{ background: '#fff7ed', borderRadius: 14, padding: 12 }}>
                    {importResult.failures.map((failure) => (
                      <div key={`${failure.index}-${failure.field}`}>
                        第 {failure.index + 1} 条，字段 {failure.field}：{failure.reason}
                      </div>
                    ))}
                  </div>
                ) : null}
              </div>
            ) : null}
          </FormCard>
        </div>
      ) : null}

      <div style={{ display: 'grid', gridTemplateColumns: 'minmax(320px, 0.95fr) minmax(320px, 1.05fr)', gap: 20, marginBottom: 20 }}>
        <div style={{ display: 'grid', gap: 16 }}>
          <FormCard title="文件知识源" description="上传租户知识文件，当前先支持元数据入库和额度限制校验。" submitLabel="上传文件" submitting={submitting} onSubmit={handleKnowledgeUpload} onCancel={() => setSelectedKnowledgeFile(null)}>
            <Field label="选择文件">
              <input
                type="file"
                accept=".txt,.md,.pdf,.docx,.csv,.json"
                onChange={(event) => setSelectedKnowledgeFile(event.target.files?.[0] ?? null)}
                style={inputStyle()}
              />
            </Field>
            <div style={{ color: '#64748b', fontSize: 14 }}>
              {selectedKnowledgeFile
                ? `待上传：${selectedKnowledgeFile.name} (${Math.ceil(selectedKnowledgeFile.size / 1024)} KB)`
                : '请选择一个文件。支持 txt、md、pdf、docx、csv、json，单文件上限 10 MB。'}
            </div>
          </FormCard>

          <FormCard title="网页知识源" description="录入公开网页地址，先保存来源信息，后续再接抓取与重抓。" submitLabel="创建网页来源" submitting={submitting} onSubmit={handleWebSourceCreate} onCancel={() => setWebSourceForm({ name: '', url: '' })}>
            <Field label="来源名称">
              <input
                value={webSourceForm.name}
                onChange={(event) => setWebSourceForm((current) => ({ ...current, name: event.target.value }))}
                placeholder="例如：帮助中心"
                style={inputStyle()}
              />
            </Field>
            <Field label="网页地址">
              <input
                value={webSourceForm.url}
                onChange={(event) => setWebSourceForm((current) => ({ ...current, url: event.target.value }))}
                placeholder="https://example.com/help"
                style={inputStyle()}
              />
            </Field>
          </FormCard>
        </div>

        <div style={{ background: '#fff', borderRadius: 20, boxShadow: '0 18px 50px rgba(15, 23, 42, 0.08)', overflow: 'hidden' }}>
          <div style={{ padding: 18, borderBottom: '1px solid #e2e8f0' }}>
            <strong>知识来源列表</strong>
            <p style={{ marginBottom: 0, color: '#64748b' }}>展示文件与网页来源的状态、类型和基础元数据。</p>
          </div>
          {loading ? (
            <div style={{ padding: 18, color: '#64748b' }}>加载中...</div>
          ) : knowledgeSources.length === 0 ? (
            <div style={{ padding: 18, color: '#64748b' }}>当前 Chatbot 还没有知识来源。</div>
          ) : (
            <div style={{ display: 'grid' }}>
              {knowledgeSources.map((source) => (
                <button
                  key={source.id}
                  type="button"
                  onClick={() => setSelectedKnowledgeSourceId(source.id)}
                  style={{
                    display: 'grid',
                    gap: 8,
                    padding: 18,
                    border: 0,
                    borderBottom: '1px solid #e2e8f0',
                    background: selectedKnowledgeSourceId === source.id ? '#fff7ed' : '#fff',
                    textAlign: 'left',
                    cursor: 'pointer'
                  }}
                >
                  <div style={{ display: 'flex', justifyContent: 'space-between', gap: 12 }}>
                    <strong>{source.sourceName}</strong>
                    <div style={{ display: 'flex', gap: 8, flexWrap: 'wrap', justifyContent: 'flex-end' }}>
                      <span style={{ borderRadius: 999, padding: '4px 10px', fontSize: 12, fontWeight: 700, color: '#7c2d12', background: '#ffedd5' }}>
                        {source.sourceType}
                      </span>
                      <span style={{ borderRadius: 999, padding: '4px 10px', fontSize: 12, fontWeight: 700, color: '#1d4ed8', background: '#dbeafe' }}>
                        {source.status}
                      </span>
                    </div>
                  </div>
                  <div style={{ color: '#334155', fontSize: 14 }}>
                    {source.sourceType === 'FILE' ? (
                      <>
                        类型：{source.contentType || source.sourceType}
                        <br />
                        大小：{Math.max(1, Math.ceil(source.fileSizeBytes / 1024))} KB
                        <br />
                      </>
                    ) : (
                      <>
                        链接：{source.sourceUri || '未提供'}
                        <br />
                        类型：网页来源
                        <br />
                      </>
                    )}
                    上传时间：{new Date(source.createdAt).toLocaleString()}
                  </div>
                </button>
              ))}
            </div>
          )}
        </div>
      </div>

      {selectedKnowledgeSource ? (
        <div style={{ background: '#fff', borderRadius: 20, boxShadow: '0 18px 50px rgba(15, 23, 42, 0.08)', overflow: 'hidden', marginBottom: 20 }}>
          <div style={{ padding: 18, borderBottom: '1px solid #e2e8f0' }}>
            <strong>知识来源详情</strong>
            <p style={{ marginBottom: 0, color: '#64748b' }}>查看来源状态、失败原因和已记录的元数据。</p>
          </div>
          <div style={{ display: 'flex', gap: 12, flexWrap: 'wrap', padding: 18, borderBottom: '1px solid #e2e8f0' }}>
            <button type="button" onClick={() => void handleKnowledgeRefresh()} disabled={submitting} style={{ borderRadius: 999, border: 0, background: '#0f766e', color: '#fff', padding: '8px 14px', cursor: 'pointer' }}>
              刷新状态
            </button>
            <button type="button" onClick={() => void handleKnowledgeRetry()} disabled={submitting} style={{ borderRadius: 999, border: '1px solid #cbd5e1', background: '#fff', color: '#0f172a', padding: '8px 14px', cursor: 'pointer' }}>
              重试处理
            </button>
            <button type="button" onClick={() => void handleKnowledgeEnable()} disabled={submitting || selectedKnowledgeSource.status === 'ACTIVE' || selectedKnowledgeSource.status === 'DELETED'} style={{ borderRadius: 999, border: 0, background: '#166534', color: '#fff', padding: '8px 14px', cursor: 'pointer' }}>
              启用来源
            </button>
            <button type="button" onClick={() => void handleKnowledgeDisable()} disabled={submitting || selectedKnowledgeSource.status === 'DELETED'} style={{ borderRadius: 999, border: '1px solid #f59e0b', background: '#fff', color: '#92400e', padding: '8px 14px', cursor: 'pointer' }}>
              停用来源
            </button>
            <button type="button" onClick={() => void handleKnowledgeDelete()} disabled={submitting || selectedKnowledgeSource.status === 'DELETED'} style={{ borderRadius: 999, border: 0, background: '#b91c1c', color: '#fff', padding: '8px 14px', cursor: 'pointer' }}>
              删除来源
            </button>
          </div>
          <div style={{ display: 'grid', gap: 12, padding: 18, color: '#334155' }}>
            <div>名称：{selectedKnowledgeSource.sourceName}</div>
            <div>类型：{selectedKnowledgeSource.sourceType}</div>
            <div>状态：{selectedKnowledgeSource.status}</div>
            <div>来源：{selectedKnowledgeSource.sourceUri || '无'}</div>
            <div>失败原因：{selectedKnowledgeSource.failureReason || '无'}</div>
            <div>创建时间：{new Date(selectedKnowledgeSource.createdAt).toLocaleString()}</div>
            <div>
              <strong>Chunk 预览</strong>
            </div>
            {selectedKnowledgeSource.chunks.length === 0 ? (
              <div>当前还没有生成 chunk，请先刷新或重试处理。</div>
            ) : (
              <div style={{ display: 'grid', gap: 10 }}>
                {selectedKnowledgeSource.chunks.map((chunk) => (
                  <div key={chunk.id} style={{ borderRadius: 14, background: '#f8fafc', padding: 12 }}>
                    <div style={{ fontWeight: 700, marginBottom: 6 }}>Chunk #{chunk.chunkIndex + 1}</div>
                    <div style={{ color: '#475569', marginBottom: 6 }}>{chunk.summary || '无摘要'}</div>
                    <div style={{ whiteSpace: 'pre-wrap', fontSize: 14 }}>{chunk.content}</div>
                  </div>
                ))}
              </div>
            )}
            <div>
              <strong>元数据</strong>
            </div>
            {Object.keys(selectedKnowledgeSource.metadata).length === 0 ? (
              <div>暂无元数据。</div>
            ) : (
              <div style={{ display: 'grid', gap: 8 }}>
                {Object.entries(selectedKnowledgeSource.metadata).map(([key, value]) => (
                  <div key={key}>
                    {key}：{value}
                  </div>
                ))}
              </div>
            )}
          </div>
        </div>
      ) : null}

      <div style={{ display: 'grid', gridTemplateColumns: 'minmax(360px, 1fr) minmax(360px, 1fr)', gap: 20 }}>
        <section style={{ display: 'grid', gap: 16 }}>
          <div style={{ background: '#fff', borderRadius: 20, boxShadow: '0 18px 50px rgba(15, 23, 42, 0.08)', overflow: 'hidden' }}>
            <div style={{ padding: 18, borderBottom: '1px solid #e2e8f0' }}>
              <strong>FAQ 列表</strong>
              <p style={{ marginBottom: 0, color: '#64748b' }}>查看问题、语言和当前状态，并支持批量状态操作。</p>
            </div>
            <div style={{ padding: 18, borderBottom: '1px solid #e2e8f0', display: 'flex', gap: 12, flexWrap: 'wrap' }}>
              <button type="button" disabled={submitting || selectedFaqIds.length === 0} onClick={() => void handleBulkStatusChange('ACTIVE')} style={{ borderRadius: 999, border: 0, background: '#166534', color: '#fff', padding: '8px 14px', cursor: 'pointer' }}>
                批量启用
              </button>
              <button type="button" disabled={submitting || selectedFaqIds.length === 0} onClick={() => void handleBulkStatusChange('DISABLED')} style={{ borderRadius: 999, border: 0, background: '#b91c1c', color: '#fff', padding: '8px 14px', cursor: 'pointer' }}>
                批量停用
              </button>
              <button type="button" disabled={submitting || selectedFaqIds.length === 0} onClick={() => void handleBulkStatusChange('DELETED')} style={{ borderRadius: 999, border: '1px solid #cbd5e1', background: '#fff', color: '#475569', padding: '8px 14px', cursor: 'pointer' }}>
                批量删除
              </button>
            </div>
            {loading ? (
              <div style={{ padding: 18, color: '#64748b' }}>加载中...</div>
            ) : faqs.length === 0 ? (
              <div style={{ padding: 18, color: '#64748b' }}>当前 Chatbot 还没有 FAQ。</div>
            ) : (
              <div style={{ display: 'grid' }}>
                {faqs.map((faq) => {
                  const active = faq.id === selectedFaqId;
                  const badge = statusColor(faq.status);

                  return (
                    <button
                      key={faq.id}
                      type="button"
                      onClick={() => {
                        setSelectedFaqId(faq.id);
                        setEditOpen(false);
                      }}
                      style={{
                        display: 'grid',
                        gap: 8,
                        textAlign: 'left',
                        padding: 18,
                        border: 0,
                        borderBottom: '1px solid #e2e8f0',
                        background: active ? '#fff7ed' : '#fff',
                        cursor: 'pointer'
                      }}
                    >
                      <div style={{ display: 'flex', justifyContent: 'space-between', gap: 12 }}>
                        <div style={{ display: 'flex', gap: 12, alignItems: 'center' }}>
                          <input
                            type="checkbox"
                            checked={selectedFaqIds.includes(faq.id)}
                            onChange={(event) => {
                              event.stopPropagation();
                              toggleFaqSelection(faq.id, event.target.checked);
                            }}
                            onClick={(event) => event.stopPropagation()}
                          />
                          <strong>{faq.question}</strong>
                        </div>
                        <span style={{ borderRadius: 999, padding: '4px 10px', fontSize: 12, fontWeight: 700, color: badge.color, background: badge.background }}>
                          {badge.label}
                        </span>
                      </div>
                      <div style={{ color: '#334155', fontSize: 14 }}>语言：{faq.language}</div>
                    </button>
                  );
                })}
              </div>
            )}
          </div>

          {createOpen ? (
            <FormCard title="创建 FAQ" description="录入问题、相似问法和标准答案。" submitLabel="创建 FAQ" submitting={submitting} onSubmit={handleCreate} onCancel={() => setCreateOpen(false)}>
              <Field label="语言">
                <input value={createForm.language} onChange={(event) => setCreateForm((current) => ({ ...current, language: event.target.value }))} style={inputStyle()} />
              </Field>
              <Field label="主问题">
                <input value={createForm.question} onChange={(event) => setCreateForm((current) => ({ ...current, question: event.target.value }))} style={inputStyle()} />
              </Field>
              <Field label="相似问法（每行一条）">
                <textarea value={createForm.alternateQuestions.join('\n')} onChange={(event) => setCreateForm((current) => ({ ...current, alternateQuestions: normalizeLines(event.target.value) }))} style={inputStyle(true)} />
              </Field>
              <Field label="答案">
                <textarea value={createForm.answer} onChange={(event) => setCreateForm((current) => ({ ...current, answer: event.target.value }))} style={inputStyle(true)} />
              </Field>
            </FormCard>
          ) : null}
        </section>

        <section style={{ display: 'grid', gap: 16 }}>
          <div style={{ background: '#fff', borderRadius: 20, padding: 20, boxShadow: '0 18px 50px rgba(15, 23, 42, 0.08)', display: 'grid', gap: 16 }}>
            <div style={{ display: 'flex', justifyContent: 'space-between', gap: 12, alignItems: 'center' }}>
              <div>
                <h3 style={{ margin: 0 }}>FAQ 详情</h3>
                <p style={{ marginBottom: 0, color: '#64748b' }}>查看答案和相似问法，支持快速启停。</p>
              </div>
              {selectedFaq ? (
                <button type="button" onClick={() => setEditOpen((current) => !current)} style={{ borderRadius: 999, border: '1px solid #cbd5e1', background: '#fff', padding: '10px 14px', cursor: 'pointer' }}>
                  {editOpen ? '收起编辑' : '编辑'}
                </button>
              ) : null}
            </div>

            {!selectedFaq ? (
              <div style={{ color: '#64748b' }}>从左侧选择一条 FAQ 查看详情。</div>
            ) : (
              <>
                <div style={{ display: 'grid', gap: 10, color: '#334155' }}>
                  <div>问题：{selectedFaq.question}</div>
                  <div>语言：{selectedFaq.language}</div>
                  <div>答案：{selectedFaq.answer}</div>
                  <div>相似问法：{selectedFaq.alternateQuestions.length ? selectedFaq.alternateQuestions.join('；') : '暂无'}</div>
                </div>
                <div style={{ display: 'flex', gap: 12, flexWrap: 'wrap' }}>
                  <button type="button" disabled={submitting} onClick={() => void handleStatusChange('ACTIVE')} style={{ borderRadius: 999, border: 0, background: '#166534', color: '#fff', padding: '10px 16px', cursor: 'pointer' }}>
                    启用
                  </button>
                  <button type="button" disabled={submitting} onClick={() => void handleStatusChange('DISABLED')} style={{ borderRadius: 999, border: 0, background: '#b91c1c', color: '#fff', padding: '10px 16px', cursor: 'pointer' }}>
                    停用
                  </button>
                </div>
              </>
            )}
          </div>

          {editOpen && selectedFaq ? (
            <FormCard title="编辑 FAQ" description="修改问题、相似问法和答案内容。" submitLabel="保存修改" submitting={submitting} onSubmit={handleEdit} onCancel={() => setEditOpen(false)}>
              <Field label="语言">
                <input value={editForm.language} onChange={(event) => setEditForm((current) => ({ ...current, language: event.target.value }))} style={inputStyle()} />
              </Field>
              <Field label="主问题">
                <input value={editForm.question} onChange={(event) => setEditForm((current) => ({ ...current, question: event.target.value }))} style={inputStyle()} />
              </Field>
              <Field label="相似问法（每行一条）">
                <textarea value={editForm.alternateQuestions.join('\n')} onChange={(event) => setEditForm((current) => ({ ...current, alternateQuestions: normalizeLines(event.target.value) }))} style={inputStyle(true)} />
              </Field>
              <Field label="答案">
                <textarea value={editForm.answer} onChange={(event) => setEditForm((current) => ({ ...current, answer: event.target.value }))} style={inputStyle(true)} />
              </Field>
            </FormCard>
          ) : null}
        </section>
      </div>
    </>
  );
}

function ConversationsPage({ session }: { session: AuthSession }) {
  const token = session.accessToken;
  const [chatbots, setChatbots] = useState<ChatbotSummary[]>([]);
  const [chatbotId, setChatbotId] = useState<number | null>(null);
  const [status, setStatus] = useState<ConversationSummary['status'] | ''>('');
  const [conversations, setConversations] = useState<ConversationSummary[]>([]);
  const [selectedConversationId, setSelectedConversationId] = useState<number | null>(null);
  const [conversationDetail, setConversationDetail] = useState<ConversationDetail | null>(null);
  const [loading, setLoading] = useState(true);
  const [detailLoading, setDetailLoading] = useState(false);
  const [submitting, setSubmitting] = useState(false);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);
  const [notice, setNotice] = useState<string | null>(null);

  useEffect(() => {
    if (!session.tenantId) {
      return;
    }

    void listChatbots(token, session.tenantId)
      .then((nextChatbots) => setChatbots(nextChatbots))
      .catch(() => undefined);
  }, [session.tenantId, token]);

  useEffect(() => {
    setLoading(true);
    setErrorMessage(null);

    void listConversations(token, {
      chatbotId: chatbotId ?? undefined,
      status: status || undefined
    })
      .then((nextConversations) => {
        setConversations(nextConversations);
        setSelectedConversationId((current) => current ?? nextConversations[0]?.id ?? null);
      })
      .catch((error) => setErrorMessage(error instanceof Error ? error.message : '会话列表加载失败。'))
      .finally(() => setLoading(false));
  }, [chatbotId, status, token]);

  useEffect(() => {
    if (!selectedConversationId) {
      setConversationDetail(null);
      return;
    }

    setDetailLoading(true);

    void getConversation(token, selectedConversationId)
      .then((detail) => setConversationDetail(detail))
      .catch((error) => setErrorMessage(error instanceof Error ? error.message : '会话详情加载失败。'))
      .finally(() => setDetailLoading(false));
  }, [selectedConversationId, token]);

  const handleStatusChange = async (nextStatus: ConversationSummary['status']) => {
    if (!selectedConversationId) {
      return;
    }

    setSubmitting(true);
    setErrorMessage(null);
    setNotice(null);

    try {
      const updatedConversation = await updateConversationStatus(token, selectedConversationId, nextStatus);
      setConversations((current) =>
        current.map((conversation) =>
          conversation.id === updatedConversation.id ? { ...conversation, status: updatedConversation.status } : conversation
        )
      );
      setConversationDetail((current) => (current ? { ...current, status: updatedConversation.status } : current));
      setNotice(`会话状态已更新为 ${updatedConversation.status}。`);
    } catch (error) {
      setErrorMessage(error instanceof Error ? error.message : '会话状态更新失败。');
    } finally {
      setSubmitting(false);
    }
  };

  const handleExport = async () => {
    if (!selectedConversationId) {
      return;
    }

    setSubmitting(true);
    setErrorMessage(null);
    setNotice(null);

    try {
      const exportBlob = await exportConversation(token, selectedConversationId);
      const objectUrl = window.URL.createObjectURL(exportBlob);
      const anchor = document.createElement('a');
      anchor.href = objectUrl;
      anchor.download = `conversation-${selectedConversationId}.json`;
      anchor.click();
      window.URL.revokeObjectURL(objectUrl);
      setNotice(`会话 ${selectedConversationId} 已导出。`);
    } catch (error) {
      setErrorMessage(error instanceof Error ? error.message : '会话导出失败。');
    } finally {
      setSubmitting(false);
    }
  };

  const handleDelete = async () => {
    if (!selectedConversationId) {
      return;
    }

    const confirmed = window.confirm('删除后该会话将被标记为 DELETED，并写入审计日志。是否继续？');

    if (!confirmed) {
      return;
    }

    setSubmitting(true);
    setErrorMessage(null);
    setNotice(null);

    try {
      const deletedConversation = await deleteConversation(token, selectedConversationId);
      setConversations((current) =>
        current.map((conversation) =>
          conversation.id === deletedConversation.id ? { ...conversation, status: deletedConversation.status } : conversation
        )
      );
      setConversationDetail((current) => (current ? { ...current, status: deletedConversation.status } : current));
      setNotice(`会话 ${selectedConversationId} 已标记为 DELETED。`);
    } catch (error) {
      setErrorMessage(error instanceof Error ? error.message : '会话删除失败。');
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <>
      <SectionHeader title="会话管理" />

      <div style={{ display: 'grid', gap: 16 }}>
        {errorMessage ? <Banner tone="error">{errorMessage}</Banner> : null}
        {notice ? <Banner tone="notice">{notice}</Banner> : null}
      </div>

      <div style={{ display: 'grid', gridTemplateColumns: 'repeat(2, minmax(0, 280px))', gap: 16, marginTop: 16, marginBottom: 16 }}>
        <Field label="Chatbot">
          <select value={chatbotId ?? ''} onChange={(event) => setChatbotId(event.target.value ? Number(event.target.value) : null)} style={inputStyle()}>
            <option value="">全部 Chatbot</option>
            {chatbots.map((chatbot) => (
              <option key={chatbot.id} value={chatbot.id}>
                {chatbot.name}
              </option>
            ))}
          </select>
        </Field>
        <Field label="状态">
          <select value={status} onChange={(event) => setStatus(event.target.value as ConversationSummary['status'] | '')} style={inputStyle()}>
            <option value="">全部状态</option>
            <option value="ACTIVE">ACTIVE</option>
            <option value="ENDED">ENDED</option>
            <option value="HANDOFF_PENDING">HANDOFF_PENDING</option>
            <option value="DELETED">DELETED</option>
          </select>
        </Field>
      </div>

      <div style={{ display: 'grid', gridTemplateColumns: 'minmax(360px, 1fr) minmax(420px, 1fr)', gap: 20 }}>
        <section style={{ background: '#fff', borderRadius: 20, boxShadow: '0 18px 50px rgba(15, 23, 42, 0.08)', overflow: 'hidden' }}>
          <div style={{ padding: 18, borderBottom: '1px solid #e2e8f0' }}>
            <strong>会话列表</strong>
            <p style={{ marginBottom: 0, color: '#64748b' }}>按 Chatbot 和状态筛选当前租户会话。</p>
          </div>
          {loading ? (
            <div style={{ padding: 18, color: '#64748b' }}>加载中...</div>
          ) : conversations.length === 0 ? (
            <div style={{ padding: 18, color: '#64748b' }}>当前没有符合条件的会话。</div>
          ) : (
            <div style={{ display: 'grid' }}>
              {conversations.map((conversation) => {
                const active = conversation.id === selectedConversationId;
                const badge = statusColor(conversation.status);

                return (
                  <button
                    key={conversation.id}
                    type="button"
                    onClick={() => setSelectedConversationId(conversation.id)}
                    style={{
                      display: 'grid',
                      gap: 8,
                      textAlign: 'left',
                      padding: 18,
                      border: 0,
                      borderBottom: '1px solid #e2e8f0',
                      background: active ? '#fff7ed' : '#fff',
                      cursor: 'pointer'
                    }}
                  >
                    <div style={{ display: 'flex', justifyContent: 'space-between', gap: 12 }}>
                      <strong>{conversation.chatbotName}</strong>
                      <span style={{ borderRadius: 999, padding: '4px 10px', fontSize: 12, fontWeight: 700, color: badge.color, background: badge.background }}>
                        {badge.label}
                      </span>
                    </div>
                    <div style={{ color: '#475569', fontSize: 14 }}>访客：{conversation.anonymousVisitorId}</div>
                    <div style={{ color: '#334155', fontSize: 14 }}>{conversation.latestMessage || '暂无消息内容'}</div>
                  </button>
                );
              })}
            </div>
          )}
        </section>

        <section style={{ background: '#fff', borderRadius: 20, padding: 20, boxShadow: '0 18px 50px rgba(15, 23, 42, 0.08)', display: 'grid', gap: 16 }}>
          <div>
            <h3 style={{ margin: 0 }}>会话详情</h3>
            <p style={{ marginBottom: 0, color: '#64748b' }}>查看来源元数据和完整消息序列。</p>
          </div>

          {detailLoading ? (
            <div style={{ color: '#64748b' }}>详情加载中...</div>
          ) : !conversationDetail ? (
            <div style={{ color: '#64748b' }}>从左侧选择一条会话查看详情。</div>
          ) : (
            <>
              <div style={{ display: 'grid', gap: 8, color: '#334155' }}>
                <div>Chatbot：{conversationDetail.chatbotName}</div>
                <div>访客标识：{conversationDetail.anonymousVisitorId}</div>
                <div>入口：{conversationDetail.entryType}</div>
                <div>状态：{conversationDetail.status}</div>
                <div>域名：{String(conversationDetail.metadata.domain ?? '-')}</div>
                <div>IP：{String(conversationDetail.metadata.ipAddress ?? '-')}</div>
              </div>
              <div style={{ display: 'flex', gap: 12, flexWrap: 'wrap' }}>
                <button type="button" disabled={submitting} onClick={() => void handleStatusChange('ACTIVE')} style={{ borderRadius: 999, border: 0, background: '#166534', color: '#fff', padding: '10px 16px', cursor: 'pointer' }}>
                  标记 ACTIVE
                </button>
                <button type="button" disabled={submitting} onClick={() => void handleStatusChange('ENDED')} style={{ borderRadius: 999, border: 0, background: '#b45309', color: '#fff', padding: '10px 16px', cursor: 'pointer' }}>
                  标记 ENDED
                </button>
                <button type="button" disabled={submitting} onClick={() => void handleStatusChange('HANDOFF_PENDING')} style={{ borderRadius: 999, border: 0, background: '#1d4ed8', color: '#fff', padding: '10px 16px', cursor: 'pointer' }}>
                  标记 HANDOFF_PENDING
                </button>
                <button type="button" disabled={submitting} onClick={() => void handleExport()} style={{ borderRadius: 999, border: 0, background: '#475569', color: '#fff', padding: '10px 16px', cursor: 'pointer' }}>
                  导出 JSON
                </button>
                <button type="button" disabled={submitting || conversationDetail.status === 'DELETED'} onClick={() => void handleDelete()} style={{ borderRadius: 999, border: 0, background: '#7f1d1d', color: '#fff', padding: '10px 16px', cursor: 'pointer', opacity: conversationDetail.status === 'DELETED' ? 0.5 : 1 }}>
                  删除会话
                </button>
              </div>
              <div style={{ display: 'grid', gap: 12 }}>
                {conversationDetail.messages.map((message) => (
                  <div key={message.id} style={{ borderRadius: 16, padding: 14, background: message.role === 'ASSISTANT' ? '#fff7ed' : '#f8fafc', border: '1px solid #e2e8f0' }}>
                    <div style={{ display: 'flex', justifyContent: 'space-between', gap: 12, marginBottom: 8 }}>
                      <strong>{message.role}</strong>
                      <span style={{ color: '#64748b', fontSize: 12 }}>{message.createdAt}</span>
                    </div>
                    <div style={{ color: '#334155', whiteSpace: 'pre-wrap' }}>{message.content}</div>
                  </div>
                ))}
              </div>
            </>
          )}
        </section>
      </div>
    </>
  );
}

function ProtectedApp({ session, onLogout }: { session: AuthSession; onLogout: () => void }) {
  return (
    <AdminShell
      title="Tenant Admin"
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
        { to: '/', label: '工作台' },
        { to: '/usage', label: '额度' },
        { to: '/chatbots', label: 'Chatbot' },
        { to: '/knowledge', label: 'FAQ / 知识' },
        { to: '/conversations', label: '会话' }
      ]}
    >
      <Routes>
        <Route path="/" element={<Dashboard session={session} />} />
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

export function App() {
  return (
    <I18nProvider>
      <BrowserRouter>
        <AppContent />
      </BrowserRouter>
    </I18nProvider>
  );
}
