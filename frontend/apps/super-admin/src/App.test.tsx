import { cleanup, fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import * as apiClient from '@agentx/api-client';

import { App } from './App';

vi.mock('@agentx/api-client', async () => {
  const actual = await vi.importActual<typeof import('@agentx/api-client')>('@agentx/api-client');
  return {
    ...actual,
    createModelDefinition: vi.fn(),
    createModelProvider: vi.fn(),
    exportModelAnalytics: vi.fn(),
    getModelAnalytics: vi.fn(),
    listModelDefinitions: vi.fn(),
    listModelProviders: vi.fn(),
    listTenants: vi.fn(),
    listAvailableProviderModels: vi.fn(),
    setDefaultModelDefinition: vi.fn(),
    updateModelDefinitionStatus: vi.fn(),
    updateModelProviderStatus: vi.fn()
  };
});

const listTenantsMock = vi.mocked(apiClient.listTenants);
const listModelProvidersMock = vi.mocked(apiClient.listModelProviders);
const listModelDefinitionsMock = vi.mocked(apiClient.listModelDefinitions);
const listAvailableProviderModelsMock = vi.mocked(apiClient.listAvailableProviderModels);
const getModelAnalyticsMock = vi.mocked(apiClient.getModelAnalytics);
const createModelProviderMock = vi.mocked(apiClient.createModelProvider);
const createModelDefinitionMock = vi.mocked(apiClient.createModelDefinition);
const updateModelProviderStatusMock = vi.mocked(apiClient.updateModelProviderStatus);
const updateModelDefinitionStatusMock = vi.mocked(apiClient.updateModelDefinitionStatus);
const setDefaultModelDefinitionMock = vi.mocked(apiClient.setDefaultModelDefinition);
const exportModelAnalyticsMock = vi.mocked(apiClient.exportModelAnalytics);

const SESSION_KEY = 'agentx.super-admin.session';

const session = {
  userId: 1,
  tenantId: null,
  email: 'admin@example.com',
  displayName: 'Super Admin',
  roles: ['SUPER_ADMIN'],
  accessToken: 'session-token'
} satisfies apiClient.AuthSession;

const tenants: apiClient.TenantSummary[] = [
  {
    id: 1,
    code: 'tenant-a',
    name: 'Tenant A',
    status: 'ACTIVE',
    contactName: 'Alice',
    contactEmail: 'alice@example.com'
  }
];

const analytics: apiClient.ModelAnalyticsOverview = {
  totalCalls: 190,
  successCalls: 170,
  failedCalls: 20,
  failureRate: 10.5,
  totalTokens: 18200,
  totalCost: 43.2876,
  avgLatencyMs: 812,
  trends: {
    totalCalls: { currentValue: 190, previousValue: 120, deltaValue: 70, deltaPercent: 58.3 },
    totalTokens: { currentValue: 18200, previousValue: 9300, deltaValue: 8900, deltaPercent: 95.7 },
    totalCost: { currentValue: 43.2876, previousValue: 19.25, deltaValue: 24.0376, deltaPercent: 124.9 },
    failureRate: { currentValue: 10.5, previousValue: 7.5, deltaValue: 3.0, deltaPercent: 40 }
  },
  providers: [
    {
      providerCode: 'azure-openai',
      totalCalls: 120,
      failedCalls: 8,
      failureRate: 6.7,
      totalTokens: 14000,
      totalCost: 31.4,
      avgLatencyMs: 760,
      trends: {
        totalCalls: { currentValue: 120, previousValue: 90, deltaValue: 30, deltaPercent: 33.3 },
        totalTokens: { currentValue: 14000, previousValue: 8000, deltaValue: 6000, deltaPercent: 75 },
        totalCost: { currentValue: 31.4, previousValue: 16.2, deltaValue: 15.2, deltaPercent: 93.8 },
        failureRate: { currentValue: 6.7, previousValue: 5.0, deltaValue: 1.7, deltaPercent: 34 }
      }
    }
  ],
  models: [
    {
      providerCode: 'azure-openai',
      modelCode: 'gpt-4.1-mini',
      totalCalls: 120,
      failedCalls: 8,
      failureRate: 6.7,
      totalTokens: 14000,
      totalCost: 31.4,
      avgLatencyMs: 760,
      trends: {
        totalCalls: { currentValue: 120, previousValue: 90, deltaValue: 30, deltaPercent: 33.3 },
        totalTokens: { currentValue: 14000, previousValue: 8000, deltaValue: 6000, deltaPercent: 75 },
        totalCost: { currentValue: 31.4, previousValue: 16.2, deltaValue: 15.2, deltaPercent: 93.8 },
        failureRate: { currentValue: 6.7, previousValue: 5.0, deltaValue: 1.7, deltaPercent: 34 }
      }
    }
  ]
};

function buildProvider(overrides: Partial<apiClient.ModelProviderSummary> = {}): apiClient.ModelProviderSummary {
  return {
    id: 11,
    providerCode: 'azure-openai',
    displayName: 'Azure OpenAI',
    apiEndpoint: 'https://azure.example.test/openai',
    apiKeyHint: '****7654',
    status: 'ACTIVE',
    supports: 'CHAT_COMPLETION,EMBEDDING',
    transport: 'OPENAI_COMPATIBLE',
    apiKeyEnvVar: 'AZURE_OPENAI_KEY',
    apiVersion: '2024-02-15-preview',
    ...overrides
  };
}

function buildModel(overrides: Partial<apiClient.ModelDefinitionSummary> = {}): apiClient.ModelDefinitionSummary {
  return {
    id: 21,
    providerId: 11,
    providerCode: 'azure-openai',
    modelCode: 'gpt-4.1-mini',
    displayName: 'GPT 4.1 Mini',
    purpose: 'CHAT_COMPLETION',
    status: 'ACTIVE',
    isDefault: true,
    inputPricePer1k: 0.3,
    outputPricePer1k: 1.2,
    maxTokens: 4096,
    ...overrides
  };
}

function seedSession(path = '/models') {
  window.localStorage.setItem(SESSION_KEY, JSON.stringify(session));
  window.history.pushState({}, '', path);
}

function findLabeledField(container: ReturnType<typeof within>, label: string) {
  const field = container.getByText(label, { selector: 'strong' }).closest('label');
  expect(field).not.toBeNull();
  return field as HTMLLabelElement;
}

function changeFieldValue(container: ReturnType<typeof within>, label: string, value: string) {
  const field = findLabeledField(container, label);
  const input = field.querySelector('input');
  expect(input).not.toBeNull();
  fireEvent.change(input as HTMLInputElement, { target: { value } });
}

function selectFieldOption(container: ReturnType<typeof within>, label: string, optionText: string) {
  const field = findLabeledField(container, label);
  const nativeSelect = field.querySelector('select');

  if (nativeSelect) {
    const option = Array.from((nativeSelect as HTMLSelectElement).options).find((item) => item.text === optionText);
    expect(option).toBeDefined();
    fireEvent.change(nativeSelect, { target: { value: option?.value } });
    return;
  }

  const combobox = within(field).getByRole('combobox');
  const trigger = combobox.closest('.ant-select')?.querySelector('.ant-select-selector') as HTMLElement | null;
  fireEvent.mouseDown(trigger ?? combobox);
  const option = screen.getAllByText(optionText).find((node) => node.closest('.ant-select-item-option'))?.closest('.ant-select-item-option');
  expect(option).toBeDefined();
  fireEvent.click(option as HTMLElement);
}

function setupBaseMocks(
  providers: apiClient.ModelProviderSummary[] = [buildProvider()],
  models: apiClient.ModelDefinitionSummary[] = [buildModel()]
) {
  listTenantsMock.mockResolvedValue(tenants);
  listModelProvidersMock.mockImplementation(async () => providers);
  listModelDefinitionsMock.mockImplementation(async () => models);
  getModelAnalyticsMock.mockResolvedValue(analytics);
  listAvailableProviderModelsMock.mockImplementation(async (_token, providerId) =>
    providerId === 11
      ? [
          { modelCode: 'gpt-4.1-mini', displayName: 'GPT 4.1 Mini' },
          { modelCode: 'text-embedding-3-small', displayName: 'Text Embedding 3 Small' }
        ]
      : [{ modelCode: 'claude-3-5-sonnet-latest', displayName: 'Claude 3.5 Sonnet' }]
  );
}

describe('super-admin app', () => {
  let clickSpy: ReturnType<typeof vi.spyOn>;
  let createObjectUrlSpy: ReturnType<typeof vi.fn>;
  let revokeObjectUrlSpy: ReturnType<typeof vi.fn>;

  beforeEach(() => {
    vi.clearAllMocks();
    window.localStorage.clear();
    createObjectUrlSpy = vi.fn(() => 'blob:mock-model-analytics');
    revokeObjectUrlSpy = vi.fn();
    clickSpy = vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(() => {});
    URL.createObjectURL = createObjectUrlSpy;
    URL.revokeObjectURL = revokeObjectUrlSpy;
  });

  afterEach(() => {
    clickSpy.mockRestore();
    cleanup();
  });

  it('renders login page by default', () => {
    render(<App />);
    expect(screen.getByText('登录平台管理台')).toBeInTheDocument();
  });

  it('renders the models dashboard for a persisted super admin session', async () => {
    setupBaseMocks();
    seedSession();

    render(<App />);

    expect(await screen.findByText('模型提供方与模型')).toBeInTheDocument();
    expect(await screen.findByText('提供方列表')).toBeInTheDocument();
    expect(screen.getAllByText('Azure OpenAI').length).toBeGreaterThan(0);
    fireEvent.click(screen.getByRole('tab', { name: '总览' }));
    expect(await screen.findByText('统计筛选')).toBeInTheDocument();
    expect(screen.getAllByText('GPT 4.1 Mini').length).toBeGreaterThan(0);
    expect(screen.getByText('18200')).toBeInTheDocument();
    await waitFor(() => {
      expect(listAvailableProviderModelsMock).toHaveBeenCalledWith('session-token', 11);
    });
  });

  it('creates providers and models from the models page', async () => {
    let providers = [buildProvider()];
    let models = [buildModel()];

    setupBaseMocks(providers, models);
    createModelProviderMock.mockImplementation(async (_token, request) => {
      const created = buildProvider({
        id: 12,
        providerCode: request.providerCode,
        displayName: request.displayName,
        apiEndpoint: request.apiEndpoint,
        apiKeyHint: '****9876',
        status: request.status,
        supports: request.supports,
        transport: request.transport,
        apiKeyEnvVar: request.apiKeyEnvVar ?? null,
        apiVersion: request.apiVersion ?? null
      });
      providers = [...providers, created];
      setupBaseMocks(providers, models);
      return created;
    });
    createModelDefinitionMock.mockImplementation(async (_token, providerId, request) => {
      const provider = providers.find((item) => item.id === providerId);
      const created = buildModel({
        id: 22,
        providerId,
        providerCode: provider?.providerCode ?? 'anthropic',
        modelCode: request.modelCode,
        displayName: request.displayName,
        purpose: request.purpose,
        status: request.status,
        isDefault: request.isDefault,
        inputPricePer1k: request.inputPricePer1k,
        outputPricePer1k: request.outputPricePer1k,
        maxTokens: request.maxTokens
      });
      models = [...models, created];
      setupBaseMocks(providers, models);
      return created;
    });
    seedSession();

    render(<App />);

    fireEvent.click(await screen.findByRole('tab', { name: '提供方管理' }));
    fireEvent.click(screen.getByRole('button', { name: '新增提供方' }));

    const providerForm = within(await screen.findByRole('dialog'));
    changeFieldValue(providerForm, '提供方编码', 'anthropic');
    changeFieldValue(providerForm, '显示名称', 'Anthropic');
    selectFieldOption(providerForm, '接入方式', 'ANTHROPIC');
    changeFieldValue(providerForm, 'API Key Env Var', 'ANTHROPIC_API_KEY');
    changeFieldValue(providerForm, 'Supports', 'CHAT_COMPLETION');
    fireEvent.submit(providerForm.getByRole('button', { name: '创建提供方' }).closest('form') as HTMLFormElement);

    await waitFor(() => {
      expect(createModelProviderMock).toHaveBeenCalledWith(
        'session-token',
        expect.objectContaining({
          providerCode: 'anthropic',
          displayName: 'Anthropic',
          transport: 'ANTHROPIC',
          apiKeyEnvVar: 'ANTHROPIC_API_KEY'
        })
      );
    });
    expect(await screen.findByText('Provider Anthropic 已创建。')).toBeInTheDocument();

    fireEvent.click(screen.getAllByRole('button', { name: '新增模型' })[0]);

    const modelForm = within(await screen.findByRole('dialog'));
    selectFieldOption(modelForm, '提供方', 'Anthropic');

    await waitFor(() => {
      expect(listAvailableProviderModelsMock).toHaveBeenCalledWith('session-token', 12);
    });

    fireEvent.change(modelForm.getByPlaceholderText('可选择或手动输入模型编码'), {
      target: { value: 'claude-3-5-sonnet-latest' }
    });

    await waitFor(() => {
      expect(findLabeledField(modelForm, '显示名称').querySelector('input')).toHaveValue('Claude 3.5 Sonnet');
    });

    changeFieldValue(modelForm, '输入单价 / 1k', '0.8');
    changeFieldValue(modelForm, '输出单价 / 1k', '2.4');
    changeFieldValue(modelForm, 'Max Tokens', '8192');
    fireEvent.submit(modelForm.getByRole('button', { name: '创建模型' }).closest('form') as HTMLFormElement);

    await waitFor(() => {
      expect(createModelDefinitionMock).toHaveBeenCalledWith(
        'session-token',
        12,
        expect.objectContaining({
          modelCode: 'claude-3-5-sonnet-latest',
          displayName: 'Claude 3.5 Sonnet',
          maxTokens: 8192
        })
      );
    });
    expect(await screen.findByText('模型 Claude 3.5 Sonnet 已创建。')).toBeInTheDocument();

    fireEvent.click(screen.getByRole('button', { name: 'Anthropic' }));
    const providerDialog = within(await screen.findByRole('dialog'));
    expect(
      providerDialog.getByText((_, element) => element?.tagName === 'DIV' && element.textContent === '名称：Anthropic')
    ).toBeInTheDocument();
    expect(
      providerDialog.getAllByText(
        (_, element) => element?.tagName === 'DIV' && (element.textContent?.includes('claude-3-5-sonnet-latest') ?? false)
      ).length
    ).toBeGreaterThan(0);
  });

  it('updates provider and model operations and exports analytics', async () => {
    let providers = [buildProvider()];
    let models = [
      buildModel(),
      buildModel({ id: 22, modelCode: 'gpt-4o-mini', displayName: 'GPT 4o Mini', isDefault: false })
    ];

    setupBaseMocks(providers, models);
    updateModelProviderStatusMock.mockImplementation(async (_token, providerId, status) => {
      providers = providers.map((provider) =>
        provider.id === providerId ? { ...provider, status } : provider
      );
      setupBaseMocks(providers, models);
      return providers.find((provider) => provider.id === providerId) as apiClient.ModelProviderSummary;
    });
    updateModelDefinitionStatusMock.mockImplementation(async (_token, modelId, status) => {
      models = models.map((model) => (model.id === modelId ? { ...model, status } : model));
      setupBaseMocks(providers, models);
      return models.find((model) => model.id === modelId) as apiClient.ModelDefinitionSummary;
    });
    setDefaultModelDefinitionMock.mockImplementation(async (_token, modelId) => {
      models = models.map((model) => ({ ...model, isDefault: model.id === modelId }));
      setupBaseMocks(providers, models);
      return models.find((model) => model.id === modelId) as apiClient.ModelDefinitionSummary;
    });
    exportModelAnalyticsMock.mockResolvedValue(new Blob(['metric,value\ntotalCalls,190'], { type: 'text/csv' }));
    seedSession();

    render(<App />);

    fireEvent.click(await screen.findByRole('button', { name: /Azure OpenAI/ }));

    const providerDialog = within(await screen.findByRole('dialog'));
    expect(providerDialog.getAllByText('提供方详情').length).toBeGreaterThan(0);

    fireEvent.click(providerDialog.getByRole('button', { name: '停用提供方' }));
    await waitFor(() => {
      expect(updateModelProviderStatusMock).toHaveBeenCalledWith('session-token', 11, 'DISABLED');
    });
    expect(await screen.findByText('Provider Azure OpenAI 状态已更新为 DISABLED。')).toBeInTheDocument();

    fireEvent.click(providerDialog.getAllByRole('button', { name: '停用' })[0]);
    await waitFor(() => {
      expect(updateModelDefinitionStatusMock).toHaveBeenCalledWith('session-token', 21, 'DISABLED');
    });

    fireEvent.click(providerDialog.getAllByRole('button', { name: '设为默认' })[1]);
    await waitFor(() => {
      expect(setDefaultModelDefinitionMock).toHaveBeenCalledWith('session-token', 22);
    });
    expect(await screen.findByText('模型 GPT 4o Mini 已设为默认。')).toBeInTheDocument();

    fireEvent.click(document.querySelector('.ant-modal-close') as HTMLElement);
    fireEvent.click(screen.getByRole('tab', { name: '提供方管理' }));
    fireEvent.click(screen.getByRole('button', { name: '导出 CSV' }));
    await waitFor(() => {
      expect(exportModelAnalyticsMock).toHaveBeenCalledWith(
        'session-token',
        expect.objectContaining({
          rowLimit: 8,
          createdFrom: expect.any(String),
          createdTo: expect.any(String)
        })
      );
    });
    expect(await screen.findByText('模型统计已导出为 CSV。')).toBeInTheDocument();
    expect(createObjectUrlSpy).toHaveBeenCalledTimes(1);
    expect(clickSpy).toHaveBeenCalledTimes(1);
    expect(revokeObjectUrlSpy).toHaveBeenCalledWith('blob:mock-model-analytics');
  });
});
