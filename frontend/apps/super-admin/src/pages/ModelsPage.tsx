import type { FormEvent } from 'react';
import { useEffect, useState } from 'react';

import { AutoComplete, Checkbox, Input, InputNumber, Select } from 'antd';
import { ActionButton, ActionToolbar, DetailModal, FilterSection, ListSection, ListTable, ModalActionBar, NoticeBanner, PageStack, RowActionBar, RowActionButton, SectionActionHeader, SectionHeader, StatCard, StatusTag, SurfaceCard, TableLinkButton, WorkspaceTabs } from '@agentx/admin-ui';
import {
  createModelDefinition,
  createModelProvider,
  exportModelAnalytics,
  getModelAnalytics,
  listAvailableProviderModels,
  listModelDefinitions,
  listModelProviders,
  listTenants,
  setDefaultModelDefinition,
  type AuthSession,
  type AvailableModelOption,
  type CreateModelDefinitionRequest,
  type CreateModelProviderRequest,
  type GetModelAnalyticsRequest,
  type ModelAnalyticsOverview,
  type ModelDefinitionSummary,
  type ModelProviderSummary,
  type TenantSummary,
  updateModelDefinitionStatus,
  updateModelProviderStatus
} from '@agentx/api-client';

import { Field, inputStyle, TenantFormCard } from '../form-ui';

type ModelProviderCreateFormState = CreateModelProviderRequest;
type ModelDefinitionCreateFormState = {
  providerId: string;
  modelCode: string;
  displayName: string;
  purpose: ModelDefinitionSummary['purpose'];
  status: ModelDefinitionSummary['status'];
  isDefault: boolean;
  inputPricePer1k: string;
  outputPricePer1k: string;
  maxTokens: string;
};
type AnalyticsWindowValue = '7d' | '30d' | '90d' | 'all' | 'custom';
type AnalyticsFilterFormState = {
  tenantId: string;
  providerCode: string;
  modelCode: string;
  window: AnalyticsWindowValue;
  createdFrom: string;
  createdTo: string;
  rowLimit: string;
};

const analyticsWindowOptions: Array<{ value: AnalyticsWindowValue; label: string }> = [
  { value: '7d', label: '近 7 天' },
  { value: '30d', label: '近 30 天' },
  { value: '90d', label: '近 90 天' },
  { value: 'all', label: '全部时间' },
  { value: 'custom', label: '自定义区间' }
];

function normalizeFormValue(value: string) {
  return value.trim();
}

function normalizeOptionalValue(value: string) {
  return normalizeFormValue(value);
}

function resolveAnalyticsWindow(windowValue: AnalyticsWindowValue) {
  if (windowValue === 'all') {
    return {};
  }

  if (windowValue === 'custom') {
    return null;
  }

  const now = new Date();
  const days = windowValue === '7d' ? 7 : windowValue === '30d' ? 30 : 90;
  const createdFrom = new Date(now.getTime() - days * 24 * 60 * 60 * 1000);
  return {
    createdFrom: createdFrom.toISOString(),
    createdTo: now.toISOString()
  };
}

function buildAnalyticsRequest(form: AnalyticsFilterFormState): { request: GetModelAnalyticsRequest | null; error: string | null } {
  const tenantId = form.tenantId.trim();
  const providerCode = normalizeOptionalValue(form.providerCode);
  const modelCode = normalizeOptionalValue(form.modelCode);
  const rowLimit = form.rowLimit.trim();

  if (tenantId && Number.isNaN(Number(tenantId))) {
    return { request: null, error: 'Tenant ID 必须是数字。' };
  }

  if (rowLimit && Number.isNaN(Number(rowLimit))) {
    return { request: null, error: 'Top N 必须是数字。' };
  }

  const presetWindow = resolveAnalyticsWindow(form.window);
  let createdFrom = presetWindow?.createdFrom;
  let createdTo = presetWindow?.createdTo;

  if (form.window === 'custom') {
    createdFrom = form.createdFrom ? new Date(form.createdFrom).toISOString() : undefined;
    createdTo = form.createdTo ? new Date(form.createdTo).toISOString() : undefined;
    if (createdFrom && createdTo && createdFrom > createdTo) {
      return { request: null, error: '自定义时间范围无效：开始时间不能晚于结束时间。' };
    }
  }

  return {
    request: {
      tenantId: tenantId ? Number(tenantId) : undefined,
      providerCode: providerCode || undefined,
      modelCode: modelCode || undefined,
      createdFrom,
      createdTo,
      rowLimit: rowLimit ? Number(rowLimit) : undefined
    },
    error: null
  };
}

export function ModelsPage({ session }: { session: AuthSession }) {
  const token = session.accessToken;
  const [tenants, setTenants] = useState<TenantSummary[]>([]);
  const [providers, setProviders] = useState<ModelProviderSummary[]>([]);
  const [models, setModels] = useState<ModelDefinitionSummary[]>([]);
  const [analytics, setAnalytics] = useState<ModelAnalyticsOverview | null>(null);
  const [loading, setLoading] = useState(true);
  const [submitting, setSubmitting] = useState(false);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);
  const [notice, setNotice] = useState<string | null>(null);
  const [workspaceTab, setWorkspaceTab] = useState('providers');
  const [providerDetailOpen, setProviderDetailOpen] = useState(false);
  const [providerCreateOpen, setProviderCreateOpen] = useState(false);
  const [modelCreateOpen, setModelCreateOpen] = useState(false);
  const [selectedProviderId, setSelectedProviderId] = useState<number | null>(null);
  const [analyticsFilterForm, setAnalyticsFilterForm] = useState<AnalyticsFilterFormState>({
    tenantId: '',
    providerCode: '',
    modelCode: '',
    window: '30d',
    createdFrom: '',
    createdTo: '',
    rowLimit: '8'
  });
  const [appliedAnalyticsFilters, setAppliedAnalyticsFilters] = useState<GetModelAnalyticsRequest>(
    buildAnalyticsRequest({
      tenantId: '',
      providerCode: '',
      modelCode: '',
      window: '30d',
      createdFrom: '',
      createdTo: '',
      rowLimit: '8'
    }).request ?? {}
  );
  const [providerForm, setProviderForm] = useState<ModelProviderCreateFormState>({
    providerCode: '',
    displayName: '',
    apiEndpoint: '',
    apiKey: '',
    status: 'ACTIVE',
    supports: 'CHAT_COMPLETION',
    transport: 'BUILTIN',
    apiKeyEnvVar: '',
    apiVersion: ''
  });
  const [modelForm, setModelForm] = useState<ModelDefinitionCreateFormState>({
    providerId: '',
    modelCode: '',
    displayName: '',
    purpose: 'CHAT_COMPLETION',
    status: 'ACTIVE',
    isDefault: true,
    inputPricePer1k: '0',
    outputPricePer1k: '0',
    maxTokens: '2048'
  });
  const [availableProviderModels, setAvailableProviderModels] = useState<AvailableModelOption[]>([]);
  const [availableProviderModelsLoading, setAvailableProviderModelsLoading] = useState(false);

  const selectedProvider = providers.find((provider) => provider.id === selectedProviderId) ?? null;
  const providerModels = models.filter((model) => model.providerId === selectedProviderId);
  const analyticsModelOptions = analyticsFilterForm.providerCode ? models.filter((model) => model.providerCode === analyticsFilterForm.providerCode) : models;
  const tenantNameById = new Map(tenants.map((tenant) => [tenant.id, tenant.name]));
  const providerNameByCode = new Map(providers.map((provider) => [provider.providerCode, provider.displayName]));
  const modelNameByKey = new Map(models.map((model) => [`${model.providerCode}::${model.modelCode}`, model.displayName]));
  const selectedAnalyticsTenantName = appliedAnalyticsFilters.tenantId == null ? null : (tenantNameById.get(appliedAnalyticsFilters.tenantId) ?? null);
  const selectedAnalyticsProviderName = appliedAnalyticsFilters.providerCode == null ? null : (providerNameByCode.get(appliedAnalyticsFilters.providerCode) ?? null);
  const selectedAnalyticsModelName =
    appliedAnalyticsFilters.providerCode == null || appliedAnalyticsFilters.modelCode == null
      ? null
      : (modelNameByKey.get(`${appliedAnalyticsFilters.providerCode}::${appliedAnalyticsFilters.modelCode}`) ?? null);

  const formatTrendPercent = (value: number | null) => {
    if (value == null) {
      return 'n/a';
    }
    return `${value >= 0 ? '+' : ''}${value.toFixed(1)}%`;
  };

  const formatTrendDelta = (value: number, fractionDigits = 0) => `${value >= 0 ? '+' : ''}${value.toFixed(fractionDigits)}`;

  const buildTrendLabel = (value: number | null, digits = 0, suffix = '') => {
    if (value == null) {
      return '无对比基线';
    }
    return `环比 ${formatTrendPercent(value)}${suffix ? ` / ${suffix}` : ''}`;
  };

  const loadData = async (preferredProviderId?: number, analyticsRequest: GetModelAnalyticsRequest = appliedAnalyticsFilters) => {
    setLoading(true);
    setErrorMessage(null);
    try {
      const [nextTenants, nextProviders, nextModels, nextAnalytics] = await Promise.all([
        listTenants(token),
        listModelProviders(token),
        listModelDefinitions(token),
        getModelAnalytics(token, analyticsRequest)
      ]);
      setTenants(nextTenants);
      setProviders(nextProviders);
      setModels(nextModels);
      setAnalytics(nextAnalytics);
      const nextSelectedProviderId =
        preferredProviderId ??
        (nextProviders.some((provider) => provider.id === selectedProviderId) ? selectedProviderId : (nextProviders[0]?.id ?? null));
      setSelectedProviderId(nextSelectedProviderId);
      setModelForm((current) => ({
        ...current,
        providerId: nextSelectedProviderId == null ? '' : String(nextSelectedProviderId)
      }));
    } catch (error) {
      setErrorMessage(error instanceof Error ? error.message : '模型数据加载失败。');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    void loadData();
  }, [token, appliedAnalyticsFilters]);

  const handleApplyAnalyticsFilters = () => {
    const nextRequest = buildAnalyticsRequest(analyticsFilterForm);
    if (nextRequest.error) {
      setErrorMessage(nextRequest.error);
      return;
    }
    setErrorMessage(null);
    setAppliedAnalyticsFilters(nextRequest.request ?? {});
  };

  const handleResetAnalyticsFilters = () => {
    const nextForm = { tenantId: '', providerCode: '', modelCode: '', window: '30d' as AnalyticsWindowValue, createdFrom: '', createdTo: '', rowLimit: '8' };
    setAnalyticsFilterForm(nextForm);
    setErrorMessage(null);
    setAppliedAnalyticsFilters(buildAnalyticsRequest(nextForm).request ?? {});
  };

  const handleExportAnalytics = async () => {
    setSubmitting(true);
    setErrorMessage(null);
    setNotice(null);
    try {
      const exportBlob = await exportModelAnalytics(token, appliedAnalyticsFilters);
      const objectUrl = window.URL.createObjectURL(exportBlob);
      const anchor = document.createElement('a');
      anchor.href = objectUrl;
      anchor.download = 'model-analytics.csv';
      anchor.click();
      window.URL.revokeObjectURL(objectUrl);
      setNotice('模型统计已导出为 CSV。');
    } catch (error) {
      setErrorMessage(error instanceof Error ? error.message : '模型统计导出失败。');
    } finally {
      setSubmitting(false);
    }
  };

  const handleCreateProvider = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    setSubmitting(true);
    setErrorMessage(null);
    setNotice(null);
    try {
      const created = await createModelProvider(token, {
        providerCode: normalizeFormValue(providerForm.providerCode),
        displayName: normalizeFormValue(providerForm.displayName),
        apiEndpoint: normalizeOptionalValue(providerForm.apiEndpoint),
        apiKey: normalizeOptionalValue(providerForm.apiKey ?? ''),
        status: providerForm.status,
        supports: normalizeOptionalValue(providerForm.supports),
        transport: providerForm.transport,
        apiKeyEnvVar: normalizeOptionalValue(providerForm.apiKeyEnvVar ?? ''),
        apiVersion: normalizeOptionalValue(providerForm.apiVersion ?? '')
      });
      setProviderForm({
        providerCode: '',
        displayName: '',
        apiEndpoint: '',
        apiKey: '',
        status: 'ACTIVE',
        supports: 'CHAT_COMPLETION',
        transport: 'BUILTIN',
        apiKeyEnvVar: '',
        apiVersion: ''
      });
      setNotice(`Provider ${created.displayName} 已创建。`);
      setProviderCreateOpen(false);
      await loadData(created.id, appliedAnalyticsFilters);
    } catch (error) {
      setErrorMessage(error instanceof Error ? error.message : 'Provider 创建失败。');
    } finally {
      setSubmitting(false);
    }
  };

  const handleCreateModel = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    if (!modelForm.providerId) {
      setErrorMessage('请先选择 provider。');
      return;
    }
    setSubmitting(true);
    setErrorMessage(null);
    setNotice(null);
    try {
      const created = await createModelDefinition(token, Number(modelForm.providerId), {
        modelCode: normalizeFormValue(modelForm.modelCode),
        displayName: normalizeFormValue(modelForm.displayName),
        purpose: modelForm.purpose,
        status: modelForm.status,
        isDefault: modelForm.isDefault,
        inputPricePer1k: Number(modelForm.inputPricePer1k),
        outputPricePer1k: Number(modelForm.outputPricePer1k),
        maxTokens: Number(modelForm.maxTokens)
      } as CreateModelDefinitionRequest);
      setModelForm((current) => ({
        ...current,
        modelCode: '',
        displayName: '',
        inputPricePer1k: '0',
        outputPricePer1k: '0',
        maxTokens: '2048'
      }));
      setNotice(`模型 ${created.displayName} 已创建。`);
      setModelCreateOpen(false);
      await loadData(Number(modelForm.providerId), appliedAnalyticsFilters);
    } catch (error) {
      setErrorMessage(error instanceof Error ? error.message : '模型创建失败。');
    } finally {
      setSubmitting(false);
    }
  };

  const loadAvailableModels = async (providerIdValue: string) => {
    if (!providerIdValue) {
      setAvailableProviderModels([]);
      return;
    }

    setAvailableProviderModelsLoading(true);
    try {
      const nextOptions = await listAvailableProviderModels(token, Number(providerIdValue));
      setAvailableProviderModels(nextOptions);
    } catch (error) {
      setAvailableProviderModels([]);
      setErrorMessage(error instanceof Error ? error.message : '可选模型加载失败。');
    } finally {
      setAvailableProviderModelsLoading(false);
    }
  };

  useEffect(() => {
    void loadAvailableModels(modelForm.providerId);
  }, [token, modelForm.providerId]);

  const handleProviderStatusChange = async (providerId: number, status: ModelProviderSummary['status']) => {
    setSubmitting(true);
    setErrorMessage(null);
    setNotice(null);
    try {
      const updated = await updateModelProviderStatus(token, providerId, status);
      setNotice(`Provider ${updated.displayName} 状态已更新为 ${updated.status}。`);
      await loadData(providerId, appliedAnalyticsFilters);
    } catch (error) {
      setErrorMessage(error instanceof Error ? error.message : 'Provider 状态更新失败。');
    } finally {
      setSubmitting(false);
    }
  };

  const handleModelStatusChange = async (modelId: number, status: ModelDefinitionSummary['status']) => {
    setSubmitting(true);
    setErrorMessage(null);
    setNotice(null);
    try {
      const updated = await updateModelDefinitionStatus(token, modelId, status);
      setNotice(`模型 ${updated.displayName} 状态已更新为 ${updated.status}。`);
      await loadData(updated.providerId, appliedAnalyticsFilters);
    } catch (error) {
      setErrorMessage(error instanceof Error ? error.message : '模型状态更新失败。');
    } finally {
      setSubmitting(false);
    }
  };

  const handleSetDefaultModel = async (modelId: number) => {
    setSubmitting(true);
    setErrorMessage(null);
    setNotice(null);
    try {
      const updated = await setDefaultModelDefinition(token, modelId);
      setNotice(`模型 ${updated.displayName} 已设为默认。`);
      await loadData(updated.providerId, appliedAnalyticsFilters);
    } catch (error) {
      setErrorMessage(error instanceof Error ? error.message : '默认模型更新失败。');
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <>
      <SectionHeader title="模型提供方与模型" />

      <PageStack>
        {errorMessage ? <NoticeBanner tone="error">{errorMessage}</NoticeBanner> : null}
        {notice ? <NoticeBanner tone="notice">{notice}</NoticeBanner> : null}
      </PageStack>

      <WorkspaceTabs
        activeKey={workspaceTab}
        onChange={setWorkspaceTab}
        items={[
          {
            key: 'providers',
            label: '提供方管理',
            children: (
              <PageStack gap={20}>
                <ListSection
                  title="提供方列表"
                  description="在列表页直接完成新增、查看、启停和新增模型，详情仅通过点击名称或查看按钮打开。"
                  actions={
                    <SectionActionHeader
                      description={`当前选中提供方：${selectedProvider?.displayName ?? '未选择'}。创建和行内操作都保留在列表页。`}
                      actions={
                        <ActionToolbar>
                          <ActionButton onClick={() => setProviderCreateOpen(true)}>新增提供方</ActionButton>
                          <ActionButton onClick={() => setModelCreateOpen(true)} tone="success">新增模型</ActionButton>
                          <ActionButton onClick={() => void handleExportAnalytics()} tone="neutral">导出 CSV</ActionButton>
                        </ActionToolbar>
                      }
                    />
                  }
                >
                    <ListTable
                      rowKey="id"
                      dataSource={providers}
                      loading={loading}
                      emptyText="还没有提供方，先创建一个。"
                      columns={[
                        {
                          key: 'displayName',
                          title: '提供方名称',
                          render: (provider) => (
                            <TableLinkButton
                              onClick={() => {
                                setSelectedProviderId(provider.id);
                                setProviderDetailOpen(true);
                              }}
                            >
                              {provider.displayName}
                            </TableLinkButton>
                          )
                        },
                        { key: 'providerCode', title: '编码', render: (provider) => provider.providerCode },
                        { key: 'transport', title: '接入方式', render: (provider) => <StatusTag color="processing">{provider.transport}</StatusTag> },
                        { key: 'supports', title: '能力', render: (provider) => provider.supports || 'N/A' },
                        { key: 'status', title: '状态', render: (provider) => <StatusTag color={provider.status === 'ACTIVE' ? 'success' : 'error'}>{provider.status}</StatusTag> },
                        {
                          key: 'actions',
                          title: '操作',
                          width: 260,
                          render: (provider) => (
                            <RowActionBar>
                              <RowActionButton onClick={() => { setSelectedProviderId(provider.id); setProviderDetailOpen(true); }}>查看</RowActionButton>
                              <RowActionButton onClick={() => void handleProviderStatusChange(provider.id, provider.status === 'ACTIVE' ? 'DISABLED' : 'ACTIVE')} disabled={submitting}>
                                {provider.status === 'ACTIVE' ? '停用' : '启用'}
                              </RowActionButton>
                              <RowActionButton onClick={() => { setSelectedProviderId(provider.id); setModelForm((current) => ({ ...current, providerId: String(provider.id) })); setModelCreateOpen(true); }}>
                                新增模型
                              </RowActionButton>
                            </RowActionBar>
                          )
                        }
                      ]}
                    />
                </ListSection>

                <SurfaceCard title="当前提供方摘要" description="保留当前选中提供方的模型概览，完整详情仍从弹窗查看。">
                  <div style={{ display: 'grid', gap: 12, color: '#334155' }}>
                    <div>当前选中提供方：{selectedProvider?.displayName ?? '未选择'}</div>
                    <div>模型数量：{providerModels.length}</div>
                    <div>{selectedProvider ? `接入方式：${selectedProvider.transport} / 能力：${selectedProvider.supports || 'N/A'}` : '点击列表中的提供方名称可打开详情。'}</div>
                  </div>
                </SurfaceCard>
              </PageStack>
            )
          },
          {
            key: 'overview',
            label: '总览',
            children: (
              <PageStack>
                <FilterSection
                  title="统计筛选"
                  description="支持租户、提供方、模型过滤，以及预设或自定义时间区间。"
                  columns="repeat(6, minmax(0, 1fr))"
                  actions={
                    <SectionActionHeader
                      description="统计和筛选保留在总览页签，列表操作留在提供方管理页。"
                      actions={
                        <ActionToolbar>
                          <ActionButton onClick={handleApplyAnalyticsFilters}>应用筛选</ActionButton>
                          <ActionButton onClick={handleResetAnalyticsFilters} variant="outline" tone="neutral">重置</ActionButton>
                        </ActionToolbar>
                      }
                    />
                  }
                >
                      <Field label="Tenant">
                        <Select value={analyticsFilterForm.tenantId} onChange={(value) => setAnalyticsFilterForm((current) => ({ ...current, tenantId: value }))} style={{ width: '100%' }} options={[{ value: '', label: '全部租户' }, ...tenants.map((tenant) => ({ value: String(tenant.id), label: tenant.name }))]} />
                      </Field>
                      <Field label="提供方">
                        <Select
                          value={analyticsFilterForm.providerCode}
                          onChange={(value) =>
                            setAnalyticsFilterForm((current) => ({
                              ...current,
                              providerCode: value,
                              modelCode:
                                current.modelCode && !models.some((model) => model.providerCode === value && model.modelCode === current.modelCode)
                                  ? ''
                                  : current.modelCode
                            }))
                          }
                          style={{ width: '100%' }}
                          options={[{ value: '', label: '全部提供方' }, ...providers.map((provider) => ({ value: provider.providerCode, label: provider.displayName }))]}
                        />
                      </Field>
                      <Field label="模型">
                        <Select value={analyticsFilterForm.modelCode} onChange={(value) => setAnalyticsFilterForm((current) => ({ ...current, modelCode: value }))} style={{ width: '100%' }} options={[{ value: '', label: '全部模型' }, ...analyticsModelOptions.map((model) => ({ value: model.modelCode, label: model.displayName }))]} />
                      </Field>
                      <Field label="时间窗口">
                        <Select
                          value={analyticsFilterForm.window}
                          onChange={(value) =>
                            setAnalyticsFilterForm((current) => ({
                              ...current,
                              window: value as AnalyticsWindowValue,
                              createdFrom: value === 'custom' ? current.createdFrom : '',
                              createdTo: value === 'custom' ? current.createdTo : ''
                            }))
                          }
                          style={{ width: '100%' }}
                          options={analyticsWindowOptions.map((option) => ({ value: option.value, label: option.label }))}
                        />
                      </Field>
                      <Field label="Top N">
                        <Select value={analyticsFilterForm.rowLimit} onChange={(value) => setAnalyticsFilterForm((current) => ({ ...current, rowLimit: value }))} style={{ width: '100%' }} options={[{ value: '5', label: 'Top 5' }, { value: '8', label: 'Top 8' }, { value: '10', label: 'Top 10' }, { value: '20', label: 'Top 20' }]} />
                      </Field>
                      <div style={{ display: 'grid', gridTemplateColumns: 'repeat(2, minmax(0, 1fr))', gap: 14 }}>
                        <Field label="开始时间"><Input type="datetime-local" disabled={analyticsFilterForm.window !== 'custom'} value={analyticsFilterForm.createdFrom} onChange={(event) => setAnalyticsFilterForm((current) => ({ ...current, createdFrom: event.target.value }))} style={inputStyle()} /></Field>
                        <Field label="结束时间"><Input type="datetime-local" disabled={analyticsFilterForm.window !== 'custom'} value={analyticsFilterForm.createdTo} onChange={(event) => setAnalyticsFilterForm((current) => ({ ...current, createdTo: event.target.value }))} style={inputStyle()} /></Field>
                      </div>
                    <div style={{ display: 'flex', gap: 10, flexWrap: 'wrap', color: '#475569', fontSize: 13 }}>
                      <span>当前筛选租户：{selectedAnalyticsTenantName ?? '全部'}</span>
                      <span>提供方：{selectedAnalyticsProviderName ?? '全部'}</span>
                      <span>模型：{selectedAnalyticsModelName ?? '全部'}</span>
                      <span>Top N：{appliedAnalyticsFilters.rowLimit ?? 8}</span>
                    </div>
                </FilterSection>

                <div style={{ display: 'grid', gridTemplateColumns: 'repeat(4, minmax(0, 1fr))', gap: 16 }}>
                  <StatCard title="总调用" value={analytics == null ? '-' : String(analytics.totalCalls)} description="模型调用日志总数。" />
                  <StatCard title="失败率" value={analytics == null ? '-' : `${analytics.failureRate.toFixed(1)}%`} description="失败调用占比。" />
                  <StatCard title="Token 总量" value={analytics == null ? '-' : String(analytics.totalTokens)} description="累计 prompt + completion tokens。" />
                  <StatCard title="总成本" value={analytics == null ? '-' : analytics.totalCost.toFixed(4)} description="按模型价格快照估算。" />
                </div>
                <div style={{ display: 'grid', gridTemplateColumns: 'repeat(4, minmax(0, 1fr))', gap: 16 }}>
                  <StatCard title="调用趋势" value={analytics?.trends == null ? '-' : formatTrendDelta(analytics.trends.totalCalls.deltaValue)} description={analytics?.trends == null ? '只有完整时间窗口时才显示对比。' : `上一窗口 ${analytics.trends.totalCalls.previousValue.toFixed(0)}，环比 ${formatTrendPercent(analytics.trends.totalCalls.deltaPercent)}`} />
                  <StatCard title="失败率趋势" value={analytics?.trends == null ? '-' : `${formatTrendDelta(analytics.trends.failureRate.deltaValue, 1)}%`} description={analytics?.trends == null ? '只有完整时间窗口时才显示对比。' : `上一窗口 ${analytics.trends.failureRate.previousValue.toFixed(1)}%，环比 ${formatTrendPercent(analytics.trends.failureRate.deltaPercent)}`} />
                  <StatCard title="Token 趋势" value={analytics?.trends == null ? '-' : formatTrendDelta(analytics.trends.totalTokens.deltaValue)} description={analytics?.trends == null ? '只有完整时间窗口时才显示对比。' : `上一窗口 ${analytics.trends.totalTokens.previousValue.toFixed(0)}，环比 ${formatTrendPercent(analytics.trends.totalTokens.deltaPercent)}`} />
                  <StatCard title="成本趋势" value={analytics?.trends == null ? '-' : formatTrendDelta(analytics.trends.totalCost.deltaValue, 4)} description={analytics?.trends == null ? '只有完整时间窗口时才显示对比。' : `上一窗口 ${analytics.trends.totalCost.previousValue.toFixed(4)}，环比 ${formatTrendPercent(analytics.trends.totalCost.deltaPercent)}`} />
                </div>

                <div style={{ display: 'grid', gridTemplateColumns: 'minmax(280px, 0.9fr) minmax(420px, 1.1fr)', gap: 20 }}>
                  <SurfaceCard title="提供方概览" description="按提供方汇总调用、失败率、成本与趋势。">
                    <div style={{ display: 'grid', gap: 12 }}>
                      {(analytics?.providers ?? []).map((provider) => (
                        <div key={provider.providerCode} style={{ borderRadius: 14, border: '1px solid #e2e8f0', padding: 12, background: '#f8fafc' }}>
                          <strong>{providerNameByCode.get(provider.providerCode) ?? provider.providerCode}</strong>
                          <div style={{ color: '#64748b' }}>{provider.providerCode}</div>
                          <div>调用 {provider.totalCalls}，失败率 {provider.failureRate.toFixed(1)}%</div>
                          <div>Token {provider.totalTokens}，成本 {provider.totalCost.toFixed(4)}，平均耗时 {provider.avgLatencyMs.toFixed(0)}ms</div>
                          <div style={{ color: '#475569', fontSize: 13 }}>调用趋势 {provider.trends == null ? '无对比基线' : `${formatTrendDelta(provider.trends.totalCalls.deltaValue)} / ${buildTrendLabel(provider.trends.totalCalls.deltaPercent)}`}</div>
                        </div>
                      ))}
                    </div>
                  </SurfaceCard>
                  <SurfaceCard title="模型调用排行" description="按当前筛选维度展示模型调用排行与趋势。">
                    <div style={{ display: 'grid', gap: 12 }}>
                      {(analytics?.models ?? []).map((model) => (
                        <div key={`${model.providerCode}-${model.modelCode}`} style={{ borderRadius: 14, border: '1px solid #e2e8f0', padding: 12, background: '#f8fafc' }}>
                          <strong>{modelNameByKey.get(`${model.providerCode}::${model.modelCode}`) ?? model.modelCode}</strong>
                          <div style={{ color: '#64748b' }}>{(providerNameByCode.get(model.providerCode) ?? model.providerCode)} / {model.modelCode}</div>
                          <div>调用 {model.totalCalls}，失败率 {model.failureRate.toFixed(1)}%，平均耗时 {model.avgLatencyMs.toFixed(0)}ms</div>
                          <div>Token {model.totalTokens}，成本 {model.totalCost.toFixed(4)}</div>
                          <div style={{ color: '#475569', fontSize: 13 }}>调用趋势 {model.trends == null ? '无对比基线' : `${formatTrendDelta(model.trends.totalCalls.deltaValue)} / ${buildTrendLabel(model.trends.totalCalls.deltaPercent)}`}</div>
                        </div>
                      ))}
                    </div>
                  </SurfaceCard>
                </div>
              </PageStack>
            )
          }
        ]}
      />

      <DetailModal open={providerDetailOpen && !!selectedProvider} title="提供方详情" onCancel={() => setProviderDetailOpen(false)} width={980}>
        {!selectedProvider ? (
          <div style={{ color: '#64748b' }}>从提供方列表中选择一个提供方。</div>
        ) : (
          <div style={{ display: 'grid', gap: 16 }}>
            <SurfaceCard title="提供方详情" description="查看 endpoint、环境变量绑定与模型列表。">
              <div style={{ display: 'grid', gap: 8, color: '#334155' }}>
                <div>名称：{selectedProvider.displayName}</div>
                <div>接入方式：{selectedProvider.transport}</div>
                <div>Endpoint：{selectedProvider.apiEndpoint ?? '-'}</div>
                <div>环境变量：{selectedProvider.apiKeyEnvVar ?? '-'}</div>
                <div>API Version：{selectedProvider.apiVersion ?? '-'}</div>
                <div>Key 掩码：{selectedProvider.apiKeyHint ?? '-'}</div>
              </div>
              <ModalActionBar>
                <ActionButton disabled={submitting || selectedProvider.status === 'ACTIVE'} onClick={() => void handleProviderStatusChange(selectedProvider.id, 'ACTIVE')} tone="success">启用提供方</ActionButton>
                <ActionButton disabled={submitting || selectedProvider.status === 'DISABLED'} onClick={() => void handleProviderStatusChange(selectedProvider.id, 'DISABLED')} tone="danger">停用提供方</ActionButton>
              </ModalActionBar>
            </SurfaceCard>

            <ListSection title="模型列表" description="查看当前提供方下的模型、状态和默认配置。">
              <div style={{ display: 'grid', gap: 12 }}>
                {providerModels.length === 0 ? (
                  <div style={{ color: '#64748b' }}>当前提供方还没有模型定义。</div>
                ) : (
                  providerModels.map((model) => (
                    <div key={model.id} style={{ borderRadius: 14, border: '1px solid #e2e8f0', background: '#f8fafc', padding: 14, display: 'grid', gap: 6 }}>
                      <div style={{ display: 'flex', justifyContent: 'space-between', gap: 12 }}>
                        <strong>{model.displayName}</strong>
                        <StatusTag color={model.status === 'ACTIVE' ? 'success' : 'error'}>{model.status}</StatusTag>
                      </div>
                      <div style={{ color: '#64748b' }}>{model.modelCode} / {model.purpose}</div>
                      <div>价格：输入 {model.inputPricePer1k ?? 0} / 输出 {model.outputPricePer1k ?? 0} 每 1k tokens</div>
                      <div>Max Tokens：{model.maxTokens}{model.isDefault ? ' / 默认模型' : ''}</div>
                      <ModalActionBar>
                        <ActionButton disabled={submitting || model.status === 'ACTIVE'} onClick={() => void handleModelStatusChange(model.id, 'ACTIVE')}>启用</ActionButton>
                        <ActionButton disabled={submitting || model.status === 'DISABLED'} onClick={() => void handleModelStatusChange(model.id, 'DISABLED')} tone="warning">停用</ActionButton>
                        <ActionButton disabled={submitting || model.isDefault} onClick={() => void handleSetDefaultModel(model.id)} variant="outline" tone="neutral">设为默认</ActionButton>
                      </ModalActionBar>
                    </div>
                  ))
                )}
              </div>
            </ListSection>
          </div>
        )}
      </DetailModal>

      <DetailModal open={providerCreateOpen} title="新增提供方" onCancel={() => setProviderCreateOpen(false)} width={820}>
        <TenantFormCard title="新增提供方" description="外部供应商建议使用环境变量名，不在后台持久化明文 key。" submitLabel="创建提供方" submitting={submitting} onSubmit={handleCreateProvider} onCancel={() => setProviderCreateOpen(false)}>
          <>
            <Field label="提供方编码"><Input value={providerForm.providerCode} onChange={(event) => setProviderForm((current) => ({ ...current, providerCode: event.target.value }))} required style={inputStyle()} /></Field>
            <Field label="显示名称"><Input value={providerForm.displayName} onChange={(event) => setProviderForm((current) => ({ ...current, displayName: event.target.value }))} required style={inputStyle()} /></Field>
            <Field label="接入方式">
              <Select value={providerForm.transport} onChange={(value) => setProviderForm((current) => ({ ...current, transport: value as ModelProviderSummary['transport'] }))} style={{ width: '100%' }} options={[{ value: 'BUILTIN', label: 'BUILTIN' }, { value: 'OPENAI_COMPATIBLE', label: 'OPENAI_COMPATIBLE' }, { value: 'AZURE_OPENAI', label: 'AZURE_OPENAI' }, { value: 'ANTHROPIC', label: 'ANTHROPIC' }, { value: 'QWEN_DASHSCOPE', label: 'QWEN_DASHSCOPE' }]} />
            </Field>
            <Field label="API Endpoint"><Input value={providerForm.apiEndpoint} onChange={(event) => setProviderForm((current) => ({ ...current, apiEndpoint: event.target.value }))} placeholder="OpenAI/Azure 自定义端点，DashScope 默认可留空" style={inputStyle()} /></Field>
            <Field label="API Key Env Var"><Input value={providerForm.apiKeyEnvVar ?? ''} onChange={(event) => setProviderForm((current) => ({ ...current, apiKeyEnvVar: event.target.value }))} placeholder="OPENAI_API_KEY" style={inputStyle()} /></Field>
            <Field label="API Version"><Input value={providerForm.apiVersion ?? ''} onChange={(event) => setProviderForm((current) => ({ ...current, apiVersion: event.target.value }))} placeholder="Azure/Anthropic 可选，Qwen 不需要" style={inputStyle()} /></Field>
            <Field label="API Key Hint"><Input value={providerForm.apiKey ?? ''} onChange={(event) => setProviderForm((current) => ({ ...current, apiKey: event.target.value }))} placeholder="仅用于展示掩码，可留空" style={inputStyle()} /></Field>
            <Field label="Supports"><Input value={providerForm.supports} onChange={(event) => setProviderForm((current) => ({ ...current, supports: event.target.value }))} placeholder="CHAT_COMPLETION,EMBEDDING" style={inputStyle()} /></Field>
          </>
        </TenantFormCard>
      </DetailModal>

      <DetailModal open={modelCreateOpen} title="新增模型" onCancel={() => setModelCreateOpen(false)} width={860}>
        <TenantFormCard title="新增模型" description="为指定 provider 补充聊天模型定义与价格快照。" submitLabel="创建模型" submitting={submitting} onSubmit={handleCreateModel} onCancel={() => setModelCreateOpen(false)}>
          <>
            <Field label="提供方">
              <Select value={modelForm.providerId} onChange={(value) => setModelForm((current) => ({ ...current, providerId: value, modelCode: '', displayName: '' }))} style={{ width: '100%' }} options={[{ value: '', label: '请选择提供方' }, ...providers.map((provider) => ({ value: String(provider.id), label: provider.displayName }))]} />
            </Field>
            <Field label="模型编码">
              <div style={{ display: 'grid', gap: 8 }}>
                <div style={{ display: 'flex', gap: 10, alignItems: 'center' }}>
                  <AutoComplete
                    value={modelForm.modelCode}
                    onChange={(nextModelCode) => {
                      const matchedModel = availableProviderModels.find((model) => model.modelCode === nextModelCode);
                      const currentMatchedModel = availableProviderModels.find((model) => model.modelCode === modelForm.modelCode);
                      setModelForm((current) => ({
                        ...current,
                        modelCode: nextModelCode,
                        displayName:
                          matchedModel == null || (current.displayName && current.displayName !== current.modelCode && current.displayName !== currentMatchedModel?.displayName)
                            ? current.displayName
                            : matchedModel.displayName
                      }));
                    }}
                    options={availableProviderModels.map((model) => ({ value: model.modelCode, label: model.displayName }))}
                    style={{ flex: 1 }}
                    placeholder={availableProviderModels.length === 0 ? '输入模型编码，例如 qwen-plus' : '可选择或手动输入模型编码'}
                  >
                    <Input required style={inputStyle()} />
                  </AutoComplete>
                  <ActionButton onClick={() => void loadAvailableModels(modelForm.providerId)} disabled={!modelForm.providerId || availableProviderModelsLoading || submitting} variant="outline" tone="neutral">
                    {availableProviderModelsLoading ? '刷新中...' : '刷新'}
                  </ActionButton>
                </div>
                <div style={{ color: '#64748b', fontSize: 13 }}>
                  {modelForm.providerId
                    ? availableProviderModels.length > 0
                      ? `已加载 ${availableProviderModels.length} 个可选模型，可直接选择或手动输入。`
                      : '当前 provider 没有返回可选模型列表，仍可手动输入模型编码。'
                    : '先选择 provider，系统会自动加载可选模型。'}
                </div>
              </div>
            </Field>
            <Field label="显示名称"><Input value={modelForm.displayName} onChange={(event) => setModelForm((current) => ({ ...current, displayName: event.target.value }))} required style={inputStyle()} /></Field>
            <Field label="用途">
              <Select value={modelForm.purpose} onChange={(value) => setModelForm((current) => ({ ...current, purpose: value as ModelDefinitionSummary['purpose'] }))} style={{ width: '100%' }} options={[{ value: 'CHAT_COMPLETION', label: 'CHAT_COMPLETION' }, { value: 'EMBEDDING', label: 'EMBEDDING' }]} />
            </Field>
            <Field label="输入单价 / 1k"><InputNumber step={0.0001} stringMode value={modelForm.inputPricePer1k} onChange={(value) => setModelForm((current) => ({ ...current, inputPricePer1k: value == null ? '' : String(value) }))} style={{ width: '100%' }} /></Field>
            <Field label="输出单价 / 1k"><InputNumber step={0.0001} stringMode value={modelForm.outputPricePer1k} onChange={(value) => setModelForm((current) => ({ ...current, outputPricePer1k: value == null ? '' : String(value) }))} style={{ width: '100%' }} /></Field>
            <Field label="Max Tokens"><InputNumber min={1} value={Number(modelForm.maxTokens || 0)} onChange={(value) => setModelForm((current) => ({ ...current, maxTokens: value == null ? '' : String(value) }))} style={{ width: '100%' }} /></Field>
            <label style={{ display: 'flex', gap: 10, alignItems: 'center', color: '#334155' }}>
              <Checkbox checked={modelForm.isDefault} onChange={(event) => setModelForm((current) => ({ ...current, isDefault: event.target.checked }))} />
              设为默认聊天模型
            </label>
          </>
        </TenantFormCard>
      </DetailModal>
    </>
  );
}
