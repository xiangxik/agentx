import type { FormEvent } from 'react';
import { useEffect, useState } from 'react';

import { Input, Select } from 'antd';
import { ActionButton, DetailModal, ListSection, ListTable, ModalActionBar, NoticeBanner, PageStack, RowActionBar, RowActionButton, SectionHeader, SelectionCardButton, StatusTag, SurfaceCard, TableLinkButton, WorkspaceTabs } from '@agentx/admin-ui';
import {
  addChatbotDeploymentDomain,
  ApiRequestError,
  copyChatbot,
  createChatbot,
  deleteChatbotDeploymentDomain,
  deleteChatbot,
  getChatbot,
  getChatbotDeploymentOverview,
  listChatbotDeploymentDomains,
  listChatbots,
  listModelDefinitions,
  listModelProviders,
  type AuthSession,
  type ChatbotDeploymentDomain,
  type ChatbotDeploymentOverview,
  type ChatbotDetail,
  type ChatbotSummary,
  type ModelDefinitionSummary,
  type ModelProviderSummary,
  type UpdateChatbotAppearanceRequest,
  type UpdateChatbotBehaviorRequest,
  type UpdateChatbotRequest,
  updateChatbot,
  updateChatbotAppearance,
  updateChatbotBehavior,
  updateChatbotStatus
} from '@agentx/api-client';

import { CHAT_STYLE_OPTIONS, emptyChatbotForm, Field, FormCard, inputStyle, type ChatbotFormState } from '../form-ui';
import { describeDirectModel, describeEmbeddingModel, statusColor } from '../status';

const { TextArea } = Input;

export function ChatbotsPage({ session }: { session: AuthSession }) {
  const tenantId = session.tenantId ?? 0;
  const token = session.accessToken;
  const [chatbots, setChatbots] = useState<ChatbotSummary[]>([]);
  const [modelProviders, setModelProviders] = useState<ModelProviderSummary[]>([]);
  const [modelDefinitions, setModelDefinitions] = useState<ModelDefinitionSummary[]>([]);
  const [modelCatalogLoading, setModelCatalogLoading] = useState(false);
  const [selectedChatbotId, setSelectedChatbotId] = useState<number | null>(null);
  const [detailOpen, setDetailOpen] = useState(false);
  const [detailTab, setDetailTab] = useState('behavior');
  const [loading, setLoading] = useState(true);
  const [submitting, setSubmitting] = useState(false);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);
  const [notice, setNotice] = useState<string | null>(null);
  const [createOpen, setCreateOpen] = useState(false);
  const [editOpen, setEditOpen] = useState(false);
  const [chatbotDetail, setChatbotDetail] = useState<ChatbotDetail | null>(null);
  const [deploymentOverview, setDeploymentOverview] = useState<ChatbotDeploymentOverview | null>(null);
  const [deploymentDomains, setDeploymentDomains] = useState<ChatbotDeploymentDomain[]>([]);
  const [deploymentDomainInput, setDeploymentDomainInput] = useState('');
  const [deploymentLoading, setDeploymentLoading] = useState(false);
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
    launcherPosition: 'right',
    stylePreset: 'executive'
  });
  const [behaviorForm, setBehaviorForm] = useState<UpdateChatbotBehaviorRequest>({
    fallbackMessage: '',
    allowDirectModel: false,
    allowFeedback: true,
    allowHandoff: true,
    providerCode: '',
    modelCode: '',
    embeddingProviderCode: '',
    embeddingModelCode: ''
  });

  const selectedChatbot = chatbots.find((chatbot) => chatbot.id === selectedChatbotId) ?? null;
  const selectableChatModels = modelDefinitions.filter((model) => model.status === 'ACTIVE' && model.purpose === 'CHAT_COMPLETION');
  const selectableEmbeddingModelsCatalog = modelDefinitions.filter((model) => model.status === 'ACTIVE' && model.purpose === 'EMBEDDING');
  const selectableProviders = modelProviders.filter(
    (provider) => provider.status === 'ACTIVE' && selectableChatModels.some((model) => model.providerCode === provider.providerCode)
  );
  const selectableEmbeddingProviders = modelProviders.filter(
    (provider) => provider.status === 'ACTIVE' && selectableEmbeddingModelsCatalog.some((model) => model.providerCode === provider.providerCode)
  );
  const selectableModels = behaviorForm.providerCode
    ? selectableChatModels.filter((model) => model.providerCode === behaviorForm.providerCode)
    : selectableChatModels;
  const selectableEmbeddingModels = behaviorForm.embeddingProviderCode
    ? selectableEmbeddingModelsCatalog.filter((model) => model.providerCode === behaviorForm.embeddingProviderCode)
    : selectableEmbeddingModelsCatalog;

  const loadModelCatalog = async () => {
    setModelCatalogLoading(true);
    try {
      const [nextProviders, nextModels] = await Promise.all([listModelProviders(token), listModelDefinitions(token)]);
      setModelProviders(nextProviders);
      setModelDefinitions(nextModels);
    } catch (error) {
      setErrorMessage(error instanceof Error ? error.message : '模型目录加载失败。');
    } finally {
      setModelCatalogLoading(false);
    }
  };

  useEffect(() => {
    if (!tenantId) {
      return;
    }

    setLoading(true);
    setErrorMessage(null);

    void listChatbots(token, tenantId)
      .then((nextChatbots) => {
        setChatbots(nextChatbots);
        setSelectedChatbotId((current) => (current && nextChatbots.some((chatbot) => chatbot.id === current) ? current : null));
      })
      .catch((error) => setErrorMessage(error instanceof Error ? error.message : 'Chatbot 列表加载失败。'))
      .finally(() => setLoading(false));
  }, [tenantId, token]);

  useEffect(() => {
    void loadModelCatalog();
  }, [token]);

  useEffect(() => {
    if (!selectedChatbot) {
      setDeploymentOverview(null);
      setDeploymentDomains([]);
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
          launcherPosition: detail.launcherPosition,
          stylePreset: detail.stylePreset
        });
        setBehaviorForm({
          fallbackMessage: detail.fallbackMessage,
          allowDirectModel: detail.allowDirectModel,
          allowFeedback: detail.allowFeedback,
          allowHandoff: detail.allowHandoff,
          providerCode: detail.providerCode ?? '',
          modelCode: detail.modelCode ?? '',
          embeddingProviderCode: detail.embeddingProviderCode ?? '',
          embeddingModelCode: detail.embeddingModelCode ?? ''
        });
      })
      .catch(() => setChatbotDetail(null));
  }, [selectedChatbot, token]);

  useEffect(() => {
    if (!selectedChatbot) {
      return;
    }

    setDeploymentLoading(true);
    void Promise.all([
      getChatbotDeploymentOverview(token, selectedChatbot.id),
      listChatbotDeploymentDomains(token, selectedChatbot.id)
    ])
      .then(([overview, domains]) => {
        setDeploymentOverview(overview);
        setDeploymentDomains(domains);
      })
      .catch((error) => setErrorMessage(error instanceof Error ? error.message : '部署信息加载失败。'))
      .finally(() => setDeploymentLoading(false));
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
      setNotice(`机器人 ${createdChatbot.name} 已创建。`);
      await refreshChatbots(createdChatbot.id);
    } catch (error) {
      const message =
        error instanceof ApiRequestError && error.code === 'CHATBOTS_LIMIT_REACHED'
          ? '当前套餐的机器人数量已达上限，请先调整套餐额度或停用或删除已有机器人。'
          : error instanceof Error
            ? error.message
            : '机器人创建失败。';
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
      setNotice(`机器人 ${updatedChatbot.name} 已更新。`);
    } catch (error) {
      setErrorMessage(error instanceof Error ? error.message : '机器人更新失败。');
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
      setNotice(`机器人 ${updatedChatbot.name} 状态已更新为 ${statusColor(updatedChatbot.status).label}。`);
    } catch (error) {
      setErrorMessage(error instanceof Error ? error.message : '机器人状态更新失败。');
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
      setNotice('机器人外观配置已更新。');
    } catch (error) {
      setErrorMessage(error instanceof Error ? error.message : '机器人外观配置更新失败。');
    } finally {
      setSubmitting(false);
    }
  };

  const handleCopy = async (chatbotOverride?: ChatbotSummary | null) => {
    const chatbot = chatbotOverride ?? selectedChatbot;

    if (!chatbot) {
      return;
    }

    setSubmitting(true);
    setErrorMessage(null);
    setNotice(null);

    try {
      const copiedChatbot = await copyChatbot(token, chatbot.id);
      await refreshChatbots(copiedChatbot.id);
      setNotice(`机器人 ${chatbot.name} 已复制。`);
    } catch (error) {
      setErrorMessage(error instanceof Error ? error.message : '机器人复制失败。');
    } finally {
      setSubmitting(false);
    }
  };

  const handleDelete = async (chatbotOverride?: ChatbotSummary | null) => {
    const chatbot = chatbotOverride ?? selectedChatbot;

    if (!chatbot) {
      return;
    }

    const confirmed = window.confirm('删除后该机器人将被标记为 DELETED。是否继续？');

    if (!confirmed) {
      return;
    }

    setSubmitting(true);
    setErrorMessage(null);
    setNotice(null);

    try {
      const deletedChatbot = await deleteChatbot(token, chatbot.id);
      setChatbotDetail((current) => (current?.id === deletedChatbot.id ? { ...current, status: deletedChatbot.status } : current));
      setChatbots((current) => current.map((chatbot) => (chatbot.id === deletedChatbot.id ? { ...chatbot, status: deletedChatbot.status } : chatbot)));
      setNotice(`机器人 ${deletedChatbot.name} 已标记为 DELETED。`);
    } catch (error) {
      setErrorMessage(error instanceof Error ? error.message : '机器人删除失败。');
    } finally {
      setSubmitting(false);
    }
  };

  const openPreview = (surface: 'chat-page' | 'widget', chatbotOverride?: ChatbotSummary | null) => {
    const chatbot = chatbotOverride ?? selectedChatbot;

    if (!chatbot || typeof window === 'undefined') {
      return;
    }

    const baseUrl = surface === 'chat-page' ? 'http://localhost:4173' : 'http://localhost:4174';
    window.open(`${baseUrl}?bot=${encodeURIComponent(chatbot.publicCode)}`, '_blank', 'noopener,noreferrer');
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
      setNotice('机器人行为策略已更新。');
    } catch (error) {
      setErrorMessage(error instanceof Error ? error.message : '机器人行为策略更新失败。');
    } finally {
      setSubmitting(false);
    }
  };

  const reloadDeploymentState = async (chatbotId: number) => {
    const [overview, domains] = await Promise.all([
      getChatbotDeploymentOverview(token, chatbotId),
      listChatbotDeploymentDomains(token, chatbotId)
    ]);
    setDeploymentOverview(overview);
    setDeploymentDomains(domains);
  };

  const handleAddDeploymentDomain = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    if (!selectedChatbot) {
      return;
    }

    setSubmitting(true);
    setErrorMessage(null);
    setNotice(null);

    try {
      await addChatbotDeploymentDomain(token, selectedChatbot.id, deploymentDomainInput.trim());
      await reloadDeploymentState(selectedChatbot.id);
      setDeploymentDomainInput('');
      setNotice('白名单域名已更新。');
    } catch (error) {
      setErrorMessage(error instanceof Error ? error.message : '白名单域名保存失败。');
    } finally {
      setSubmitting(false);
    }
  };

  const handleDeleteDeploymentDomain = async (domainId: number) => {
    if (!selectedChatbot) {
      return;
    }

    setSubmitting(true);
    setErrorMessage(null);
    setNotice(null);

    try {
      await deleteChatbotDeploymentDomain(token, selectedChatbot.id, domainId);
      await reloadDeploymentState(selectedChatbot.id);
      setNotice('白名单域名已移除。');
    } catch (error) {
      setErrorMessage(error instanceof Error ? error.message : '白名单域名删除失败。');
    } finally {
      setSubmitting(false);
    }
  };

  const handleCopyDeploymentValue = async (value: string, label: string) => {
    if (typeof navigator === 'undefined' || !navigator.clipboard) {
      setNotice(`${label} 已准备好，可手动复制。`);
      return;
    }

    try {
      await navigator.clipboard.writeText(value);
      setNotice(`${label} 已复制。`);
    } catch {
      setNotice(`${label} 已准备好，可手动复制。`);
    }
  };

  return (
    <>
      <SectionHeader
        title="机器人管理"
        actions={
          <ActionButton onClick={() => {
              setCreateOpen((current) => !current);
              setEditOpen(false);
            }} tone="warning">
            {createOpen ? '收起新建' : '新建机器人'}
          </ActionButton>
        }
      />

      <PageStack>
        {errorMessage ? <NoticeBanner tone="error">{errorMessage}</NoticeBanner> : null}
        {notice ? <NoticeBanner tone="notice">{notice}</NoticeBanner> : null}
      </PageStack>

      <ListSection title="机器人列表" description="列表展示关键列，点击名称查看详情，编辑和删除等常用操作放在末列。">
        <ListTable
          rowKey="id"
          dataSource={chatbots}
          loading={loading}
          emptyText="还没有机器人，先创建一个。"
          columns={[
            {
              key: 'name',
              title: '名称',
              render: (chatbot) => (
                <TableLinkButton
                  onClick={() => {
                    setSelectedChatbotId(chatbot.id);
                    setDetailTab('behavior');
                    setDetailOpen(true);
                    setEditOpen(false);
                  }}
                >
                  {chatbot.name}
                </TableLinkButton>
              )
            },
            { key: 'language', title: '语言', render: (chatbot) => chatbot.language },
            { key: 'publicCode', title: '公开码', render: (chatbot) => chatbot.publicCode },
            { key: 'directModel', title: '直连模型', render: (chatbot) => describeDirectModel(chatbot) },
            { key: 'embeddingModel', title: 'Embedding 模型', render: (chatbot) => describeEmbeddingModel(chatbot) },
            {
              key: 'status',
              title: '状态',
              render: (chatbot) => {
                const badge = statusColor(chatbot.status);
                return <StatusTag color={chatbot.status === 'ACTIVE' ? 'success' : badge.color === '#991b1b' ? 'error' : 'default'}>{badge.label}</StatusTag>;
              }
            },
            {
              key: 'actions',
              title: '操作',
              width: 280,
              render: (chatbot) => (
                <RowActionBar>
                  <RowActionButton
                    onClick={() => {
                      setSelectedChatbotId(chatbot.id);
                      setDetailTab('edit');
                      setDetailOpen(true);
                    }}
                  >
                    编辑
                  </RowActionButton>
                  <RowActionButton
                    onClick={() => {
                      void handleDelete(chatbot);
                    }}
                    danger
                    disabled={submitting}
                  >
                    删除
                  </RowActionButton>
                  <RowActionButton
                    onClick={() => {
                      void handleCopy(chatbot);
                    }}
                    disabled={submitting}
                  >
                    复制
                  </RowActionButton>
                  <RowActionButton onClick={() => openPreview('chat-page', chatbot)}>聊天页</RowActionButton>
                  <RowActionButton onClick={() => openPreview('widget', chatbot)}>嵌入组件</RowActionButton>
                </RowActionBar>
              )
            }
          ]}
        />
      </ListSection>

      <DetailModal open={createOpen} title="创建 Chatbot" onCancel={() => setCreateOpen(false)} width={860}>
        <FormCard title="创建 Chatbot" description="配置基础信息后即可在公开页面或组件中使用。" submitLabel="创建 Chatbot" submitting={submitting} onSubmit={handleCreate} onCancel={() => setCreateOpen(false)}>
          <Field label="名称">
            <Input value={createForm.name} onChange={(event) => setCreateForm((current) => ({ ...current, name: event.target.value }))} required style={inputStyle()} />
          </Field>
          <Field label="描述">
            <TextArea value={createForm.description} onChange={(event) => setCreateForm((current) => ({ ...current, description: event.target.value }))} autoSize={{ minRows: 4 }} style={inputStyle(true)} />
          </Field>
          <Field label="语言">
            <Input value={createForm.language} onChange={(event) => setCreateForm((current) => ({ ...current, language: event.target.value }))} required style={inputStyle()} />
          </Field>
          <Field label="状态">
            <Select
              value={createForm.status}
              onChange={(value) => setCreateForm((current) => ({ ...current, status: value as ChatbotSummary['status'] }))}
              style={{ width: '100%' }}
              options={[
                { value: 'DRAFT', label: '草稿' },
                { value: 'ACTIVE', label: '启用' },
                { value: 'DISABLED', label: '停用' }
              ]}
            />
          </Field>
        </FormCard>
      </DetailModal>

      <DetailModal open={detailOpen && !!selectedChatbot} title={selectedChatbot ? `${selectedChatbot.name} 详情` : 'Chatbot 详情'} onCancel={() => setDetailOpen(false)} width={980}>
        {!selectedChatbot ? (
          <div style={{ color: '#64748b' }}>从 Chatbot 列表中选择一项后再查看详情。</div>
        ) : (
          <div style={{ display: 'grid', gap: 16 }}>
            <SurfaceCard title="Chatbot 详情" description="查看默认欢迎语、兜底话术和公开访问码。">
              <div style={{ display: 'grid', gap: 10, color: '#334155' }}>
                <div>名称：{selectedChatbot.name}</div>
                <div>描述：{selectedChatbot.description || '暂无描述'}</div>
                <div>语言：{selectedChatbot.language}</div>
                <div>公开码：{selectedChatbot.publicCode}</div>
                <div>欢迎语：{chatbotDetail?.welcomeMessage ?? selectedChatbot.welcomeMessage}</div>
                <div>兜底话术：{chatbotDetail?.fallbackMessage ?? selectedChatbot.fallbackMessage}</div>
                <div>品牌标识：{chatbotDetail?.brandVisible ? '显示' : '隐藏'}</div>
                <div>启动按钮位置：{chatbotDetail?.launcherPosition ?? 'right'}</div>
                <div>页面风格：{CHAT_STYLE_OPTIONS.find((option) => option.value === chatbotDetail?.stylePreset)?.label ?? 'Executive Horizon'}</div>
                <div>直连模型：{chatbotDetail ? describeDirectModel(chatbotDetail) : describeDirectModel(selectedChatbot)}</div>
                <div>知识 Embedding：{chatbotDetail ? describeEmbeddingModel(chatbotDetail) : describeEmbeddingModel(selectedChatbot)}</div>
              </div>
              <ModalActionBar>
                <ActionButton disabled={submitting} onClick={() => void handleStatusChange('ACTIVE')} tone="success">启用</ActionButton>
                <ActionButton disabled={submitting} onClick={() => void handleStatusChange('DISABLED')} tone="danger">停用</ActionButton>
                <ActionButton disabled={submitting} onClick={() => void handleCopy()} variant="outline" tone="neutral">复制</ActionButton>
                <ActionButton disabled={submitting} onClick={() => void handleDelete()} variant="outline" tone="danger">删除</ActionButton>
              </ModalActionBar>
            </SurfaceCard>

            <WorkspaceTabs
              activeKey={detailTab}
              onChange={setDetailTab}
              items={[
                {
                  key: 'appearance',
                  label: '外观配置',
                  children: (
                    <FormCard title="外观配置" description="调整主题色、欢迎语、品牌展示和聊天页风格。" submitLabel="保存外观" submitting={submitting} onSubmit={handleAppearanceSave} onCancel={() => setAppearanceForm({ themeColor: chatbotDetail?.themeColor ?? selectedChatbot.themeColor, welcomeMessage: chatbotDetail?.welcomeMessage ?? selectedChatbot.welcomeMessage, brandVisible: chatbotDetail?.brandVisible ?? true, launcherPosition: chatbotDetail?.launcherPosition ?? 'right', stylePreset: chatbotDetail?.stylePreset ?? 'executive' })}>
                      <Field label="主题色">
                        <Input value={appearanceForm.themeColor} onChange={(event) => setAppearanceForm((current) => ({ ...current, themeColor: event.target.value }))} style={inputStyle()} />
                      </Field>
                      <Field label="页面风格">
                        <Select value={appearanceForm.stylePreset} onChange={(value) => setAppearanceForm((current) => ({ ...current, stylePreset: value }))} style={{ width: '100%' }} options={CHAT_STYLE_OPTIONS.map((option) => ({ value: option.value, label: option.label }))} />
                        <div style={{ display: 'grid', gap: 8, marginTop: 12 }}>
                          {CHAT_STYLE_OPTIONS.map((option) => {
                            const selected = appearanceForm.stylePreset === option.value;

                            return (
                              <SelectionCardButton
                                key={option.value}
                                title={option.label}
                                description={option.description}
                                accentColor={option.color}
                                selected={selected}
                                onClick={() => setAppearanceForm((current) => ({ ...current, stylePreset: option.value, themeColor: selected ? current.themeColor : option.color }))}
                              />
                            );
                          })}
                        </div>
                      </Field>
                      <Field label="欢迎语">
                        <TextArea value={appearanceForm.welcomeMessage} onChange={(event) => setAppearanceForm((current) => ({ ...current, welcomeMessage: event.target.value }))} autoSize={{ minRows: 4 }} style={inputStyle(true)} />
                      </Field>
                      <Field label="品牌标识">
                        <Select value={appearanceForm.brandVisible ? 'true' : 'false'} onChange={(value) => setAppearanceForm((current) => ({ ...current, brandVisible: value === 'true' }))} style={{ width: '100%' }} options={[{ value: 'true', label: '显示' }, { value: 'false', label: '隐藏' }]} />
                      </Field>
                      <Field label="启动按钮位置">
                        <Select value={appearanceForm.launcherPosition} onChange={(value) => setAppearanceForm((current) => ({ ...current, launcherPosition: value }))} style={{ width: '100%' }} options={[{ value: 'right', label: '右侧' }, { value: 'left', label: '左侧' }]} />
                      </Field>
                    </FormCard>
                  )
                },
                {
                  key: 'behavior',
                  label: '行为策略',
                  children: (
                    <FormCard title="行为策略" description="调整兜底回复、模型直连、知识 Embedding、反馈和转人工开关。" submitLabel="保存策略" submitting={submitting} onSubmit={handleBehaviorSave} onCancel={() => setBehaviorForm({ fallbackMessage: chatbotDetail?.fallbackMessage ?? selectedChatbot.fallbackMessage, allowDirectModel: chatbotDetail?.allowDirectModel ?? false, allowFeedback: chatbotDetail?.allowFeedback ?? true, allowHandoff: chatbotDetail?.allowHandoff ?? true, providerCode: chatbotDetail?.providerCode ?? '', modelCode: chatbotDetail?.modelCode ?? '', embeddingProviderCode: chatbotDetail?.embeddingProviderCode ?? '', embeddingModelCode: chatbotDetail?.embeddingModelCode ?? '' })}>
                      <Field label="兜底话术">
                        <TextArea value={behaviorForm.fallbackMessage} onChange={(event) => setBehaviorForm((current) => ({ ...current, fallbackMessage: event.target.value }))} autoSize={{ minRows: 4 }} style={inputStyle(true)} />
                      </Field>
                      <Field label="允许模型直连">
                        <Select value={behaviorForm.allowDirectModel ? 'true' : 'false'} onChange={(value) => setBehaviorForm((current) => ({ ...current, allowDirectModel: value === 'true' }))} style={{ width: '100%' }} options={[{ value: 'false', label: '关闭' }, { value: 'true', label: '开启' }]} />
                      </Field>
                      <Field label="模型提供方">
                        <div style={{ display: 'grid', gap: 8 }}>
                          <div style={{ display: 'flex', gap: 10, alignItems: 'center' }}>
                            <Select
                              value={behaviorForm.providerCode ?? ''}
                              onChange={(value) =>
                                setBehaviorForm((current) => ({
                                  ...current,
                                  providerCode: value,
                                  modelCode:
                                    current.modelCode && !selectableChatModels.some((model) => model.providerCode === value && model.modelCode === current.modelCode)
                                      ? ''
                                      : current.modelCode
                                }))
                              }
                              style={{ flex: 1 }}
                              options={[{ value: '', label: '跟随系统默认' }, ...selectableProviders.map((provider) => ({ value: provider.providerCode, label: provider.displayName }))]}
                            />
                            <ActionButton
                              onClick={() => void loadModelCatalog()}
                              disabled={modelCatalogLoading || submitting}
                              variant="outline"
                              tone="neutral"
                            >
                              {modelCatalogLoading ? '刷新中...' : '刷新'}
                            </ActionButton>
                          </div>
                          <div style={{ color: '#64748b', fontSize: 13 }}>仅显示已启用且已由超级管理员配置好的聊天模型提供方。</div>
                        </div>
                      </Field>
                      <Field label="聊天模型">
                        <Select
                          value={behaviorForm.modelCode ?? ''}
                          onChange={(value) => setBehaviorForm((current) => ({ ...current, modelCode: value }))}
                          style={{ width: '100%' }}
                          disabled={!!behaviorForm.providerCode && selectableModels.length === 0}
                          options={[
                            { value: '', label: behaviorForm.providerCode ? '跟随提供方默认模型' : '跟随系统默认模型' },
                            ...selectableModels.map((model) => ({ value: model.modelCode, label: model.displayName }))
                          ]}
                        />
                        <div style={{ color: '#64748b', fontSize: 13, marginTop: 8 }}>
                          {behaviorForm.providerCode
                            ? selectableModels.length > 0
                              ? `当前提供方下可选 ${selectableModels.length} 个聊天模型。`
                              : '当前提供方还没有可用聊天模型。'
                            : '不指定时，运行时会回退到系统默认聊天模型。'}
                        </div>
                      </Field>
                      <Field label="Embedding Provider">
                        <div style={{ display: 'grid', gap: 8 }}>
                          <Select
                            value={behaviorForm.embeddingProviderCode ?? ''}
                            onChange={(value) =>
                              setBehaviorForm((current) => ({
                                ...current,
                                embeddingProviderCode: value,
                                embeddingModelCode:
                                  current.embeddingModelCode && !selectableEmbeddingModelsCatalog.some((model) => model.providerCode === value && model.modelCode === current.embeddingModelCode)
                                    ? ''
                                    : current.embeddingModelCode
                              }))
                            }
                            style={{ width: '100%' }}
                            options={[{ value: '', label: '跟随系统默认' }, ...selectableEmbeddingProviders.map((provider) => ({ value: provider.providerCode, label: provider.displayName }))]}
                          />
                          <div style={{ color: '#64748b', fontSize: 13 }}>仅显示已启用且存在可用 Embedding 模型的 Provider。</div>
                        </div>
                      </Field>
                      <Field label="Embedding Model">
                        <Select
                          value={behaviorForm.embeddingModelCode ?? ''}
                          onChange={(value) => setBehaviorForm((current) => ({ ...current, embeddingModelCode: value }))}
                          style={{ width: '100%' }}
                          disabled={!!behaviorForm.embeddingProviderCode && selectableEmbeddingModels.length === 0}
                          options={[
                            { value: '', label: behaviorForm.embeddingProviderCode ? '跟随 Provider 默认 Embedding' : '跟随系统默认 Embedding' },
                            ...selectableEmbeddingModels.map((model) => ({ value: model.modelCode, label: model.displayName }))
                          ]}
                        />
                        <div style={{ color: '#64748b', fontSize: 13, marginTop: 8 }}>
                          {behaviorForm.embeddingProviderCode
                            ? selectableEmbeddingModels.length > 0
                              ? `当前 Provider 下可选 ${selectableEmbeddingModels.length} 个 Embedding 模型。`
                              : '当前 Provider 还没有可用 Embedding 模型。'
                            : '不指定时，知识刷新与检索会回退到系统默认 Embedding 模型。'}
                        </div>
                      </Field>
                      <Field label="允许反馈">
                        <Select value={behaviorForm.allowFeedback ? 'true' : 'false'} onChange={(value) => setBehaviorForm((current) => ({ ...current, allowFeedback: value === 'true' }))} style={{ width: '100%' }} options={[{ value: 'true', label: '开启' }, { value: 'false', label: '关闭' }]} />
                      </Field>
                      <Field label="允许转人工">
                        <Select value={behaviorForm.allowHandoff ? 'true' : 'false'} onChange={(value) => setBehaviorForm((current) => ({ ...current, allowHandoff: value === 'true' }))} style={{ width: '100%' }} options={[{ value: 'true', label: '开启' }, { value: 'false', label: '关闭' }]} />
                      </Field>
                    </FormCard>
                  )
                },
                {
                  key: 'deployment',
                  label: '渠道部署',
                  children: (
                    <div style={{ display: 'grid', gap: 16 }}>
                      <SurfaceCard title="部署面板" description="生成聊天页链接、嵌入脚本，并按 Chatbot 维度维护来源域名白名单。">
                        <div style={{ display: 'grid', gap: 12, color: '#334155' }}>
                          <div>公开码：{deploymentOverview?.chatbotPublicCode ?? selectedChatbot.publicCode}</div>
                          <div>聊天页：{deploymentOverview?.chatPageUrl ?? `http://localhost:5173/chat-page?bot=${selectedChatbot.publicCode}`}</div>
                          <div>脚本地址：{deploymentOverview?.widgetScriptUrl ?? 'http://localhost:5173/widget/sdk.js'}</div>
                          <div style={{ display: 'grid', gap: 6 }}>
                            <strong style={{ color: '#0f172a' }}>嵌入代码</strong>
                            <TextArea value={deploymentOverview?.widgetSnippet ?? ''} readOnly autoSize={{ minRows: 5 }} style={inputStyle(true)} />
                          </div>
                          <div>白名单域名数：{deploymentOverview?.whitelistCount ?? deploymentDomains.length}</div>
                        </div>
                        <ModalActionBar>
                          <ActionButton disabled={submitting} onClick={() => void handleCopyDeploymentValue(deploymentOverview?.chatPageUrl ?? '', '聊天页链接')} variant="outline" tone="neutral">复制聊天页链接</ActionButton>
                          <ActionButton disabled={submitting} onClick={() => void handleCopyDeploymentValue(deploymentOverview?.widgetSnippet ?? '', '嵌入代码')} tone="warning">复制嵌入代码</ActionButton>
                        </ModalActionBar>
                      </SurfaceCard>

                      <SurfaceCard title="白名单域名" description="名单为空时默认放行；配置后仅允许名单内域名初始化会话。">
                        <form onSubmit={handleAddDeploymentDomain} style={{ display: 'grid', gap: 14 }}>
                          <Field label="新增域名">
                            <Input value={deploymentDomainInput} onChange={(event) => setDeploymentDomainInput(event.target.value)} placeholder="例如 app.agentx.test 或 https://app.agentx.test/help" style={inputStyle()} />
                          </Field>
                          <ModalActionBar>
                            <ActionButton htmlType="submit" disabled={submitting || deploymentLoading} tone="warning">添加域名</ActionButton>
                            <ActionButton htmlType="button" variant="outline" tone="neutral" onClick={() => setDeploymentDomainInput('')}>清空</ActionButton>
                          </ModalActionBar>
                        </form>

                        <div style={{ marginTop: 18 }}>
                          <ListTable
                            rowKey="id"
                            dataSource={deploymentDomains}
                            loading={deploymentLoading}
                            emptyText="还没有域名白名单，当前默认为全部来源可访问。"
                            columns={[
                              { key: 'domain', title: '域名', render: (domain) => domain.domain },
                              { key: 'createdAt', title: '添加时间', render: (domain) => new Date(domain.createdAt).toLocaleString('zh-CN') },
                              {
                                key: 'actions',
                                title: '操作',
                                render: (domain) => (
                                  <RowActionBar>
                                    <RowActionButton onClick={() => void handleDeleteDeploymentDomain(domain.id)} danger disabled={submitting}>移除</RowActionButton>
                                  </RowActionBar>
                                )
                              }
                            ]}
                          />
                        </div>
                      </SurfaceCard>

                      <SurfaceCard title="最近部署访问" description="展示最近 10 次成功初始化的访客入口，便于确认来源域名、入口类型和访问时间。">
                        <ListTable
                          rowKey="id"
                          dataSource={deploymentOverview?.recentAccesses ?? []}
                          loading={deploymentLoading}
                          emptyText="还没有部署访问记录。"
                          columns={[
                            { key: 'entryType', title: '入口', render: (access) => access.entryType },
                            { key: 'domain', title: '域名', render: (access) => access.domain ?? '未提供' },
                            { key: 'ipAddress', title: 'IP', render: (access) => access.ipAddress ?? '未提供' },
                            { key: 'createdAt', title: '访问时间', render: (access) => new Date(access.createdAt).toLocaleString('zh-CN') },
                            { key: 'conversationId', title: '会话', render: (access) => access.conversationId ? `#${access.conversationId}` : '未生成' }
                          ]}
                        />
                      </SurfaceCard>
                    </div>
                  )
                },
                {
                  key: 'edit',
                  label: '基础编辑',
                  children: (
                    <FormCard title="编辑 Chatbot" description="更新基础信息和上线状态。" submitLabel="保存修改" submitting={submitting} onSubmit={handleEdit} onCancel={() => setEditOpen(false)}>
                      <Field label="名称">
                        <Input value={editForm.name} onChange={(event) => setEditForm((current) => ({ ...current, name: event.target.value }))} required style={inputStyle()} />
                      </Field>
                      <Field label="描述">
                        <TextArea value={editForm.description} onChange={(event) => setEditForm((current) => ({ ...current, description: event.target.value }))} autoSize={{ minRows: 4 }} style={inputStyle(true)} />
                      </Field>
                      <Field label="语言">
                        <Input value={editForm.language} onChange={(event) => setEditForm((current) => ({ ...current, language: event.target.value }))} required style={inputStyle()} />
                      </Field>
                      <Field label="状态">
                        <Select
                          value={editForm.status}
                          onChange={(value) => setEditForm((current) => ({ ...current, status: value as ChatbotSummary['status'] }))}
                          style={{ width: '100%' }}
                          options={[
                            { value: 'DRAFT', label: '草稿' },
                            { value: 'ACTIVE', label: '启用' },
                            { value: 'DISABLED', label: '停用' }
                          ]}
                        />
                      </Field>
                    </FormCard>
                  )
                }
              ]}
            />
          </div>
        )}
      </DetailModal>
    </>
  );
}
