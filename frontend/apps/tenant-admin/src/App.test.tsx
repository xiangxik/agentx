import { cleanup, fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import * as apiClient from '@agentx/api-client';

import { App } from './App';

vi.mock('@agentx/api-client', async () => {
  const actual = await vi.importActual<typeof import('@agentx/api-client')>('@agentx/api-client');
  return {
    ...actual,
    getChatbot: vi.fn(),
    listChatbots: vi.fn(),
    listModelDefinitions: vi.fn(),
    listModelProviders: vi.fn(),
    updateChatbotBehavior: vi.fn()
  };
});

const SESSION_KEY = 'agentx.tenant-admin.session';

const listChatbotsMock = vi.mocked(apiClient.listChatbots);
const listModelProvidersMock = vi.mocked(apiClient.listModelProviders);
const listModelDefinitionsMock = vi.mocked(apiClient.listModelDefinitions);
const getChatbotMock = vi.mocked(apiClient.getChatbot);
const updateChatbotBehaviorMock = vi.mocked(apiClient.updateChatbotBehavior);

const session = {
  userId: 11,
  tenantId: 7,
  email: 'owner@tenant.test',
  displayName: 'Tenant Owner',
  roles: ['TENANT_ADMIN'],
  accessToken: 'tenant-session-token'
} satisfies apiClient.AuthSession;

const chatbotSummary: apiClient.ChatbotSummary = {
  id: 101,
  tenantId: 7,
  name: 'Billing Bot',
  description: 'billing support',
  language: 'zh-CN',
  status: 'ACTIVE',
  publicCode: 'billing-bot-public',
  themeColor: '#2563eb',
  welcomeMessage: '你好',
  fallbackMessage: '暂时无法回答',
  allowDirectModel: true,
  providerCode: 'chat-provider',
  modelCode: 'chat-model-a',
  embeddingProviderCode: null,
  embeddingModelCode: null
};

const chatbotDetail: apiClient.ChatbotDetail = {
  ...chatbotSummary,
  brandVisible: true,
  launcherPosition: 'right',
  stylePreset: 'executive',
  allowFeedback: true,
  allowHandoff: true,
  embeddingProviderCode: null,
  embeddingModelCode: null
};

const modelProviders: apiClient.ModelProviderSummary[] = [
  {
    id: 1,
    providerCode: 'chat-provider',
    displayName: 'Chat Provider',
    apiEndpoint: 'https://chat.example.test',
    apiKeyHint: '****1234',
    status: 'ACTIVE',
    supports: 'CHAT_COMPLETION',
    transport: 'OPENAI_COMPATIBLE',
    apiKeyEnvVar: 'CHAT_PROVIDER_KEY',
    apiVersion: null
  },
  {
    id: 2,
    providerCode: 'embed-provider',
    displayName: 'Embedding Provider',
    apiEndpoint: 'https://embed.example.test',
    apiKeyHint: '****5678',
    status: 'ACTIVE',
    supports: 'EMBEDDING',
    transport: 'OPENAI_COMPATIBLE',
    apiKeyEnvVar: 'EMBED_PROVIDER_KEY',
    apiVersion: null
  }
];

const modelDefinitions: apiClient.ModelDefinitionSummary[] = [
  {
    id: 21,
    providerId: 1,
    providerCode: 'chat-provider',
    modelCode: 'chat-model-a',
    displayName: 'Chat Model A',
    purpose: 'CHAT_COMPLETION',
    status: 'ACTIVE',
    isDefault: true,
    inputPricePer1k: 0.1,
    outputPricePer1k: 0.3,
    maxTokens: 4096
  },
  {
    id: 22,
    providerId: 2,
    providerCode: 'embed-provider',
    modelCode: 'embedding-model-b',
    displayName: 'Embedding Model B',
    purpose: 'EMBEDDING',
    status: 'ACTIVE',
    isDefault: false,
    inputPricePer1k: 0.02,
    outputPricePer1k: 0,
    maxTokens: 8192
  }
];

function seedSession(path = '/chatbots') {
  window.localStorage.setItem(SESSION_KEY, JSON.stringify(session));
  window.history.pushState({}, '', path);
}

function findSelectForField(label: string) {
  const field = screen.getByText(label, { selector: 'strong' }).closest('label');
  expect(field).not.toBeNull();
  const nativeSelect = field?.querySelector('select');

  if (nativeSelect) {
    return nativeSelect as HTMLSelectElement;
  }

  return within(field as HTMLLabelElement).getByRole('combobox');
}

function selectFieldOption(label: string, optionText: string) {
  const control = findSelectForField(label);

  if (control instanceof HTMLSelectElement) {
    const option = Array.from(control.options).find((item) => item.text === optionText);
    expect(option).toBeDefined();
    fireEvent.change(control, { target: { value: option?.value } });
    return;
  }

  const trigger = control.closest('.ant-select')?.querySelector('.ant-select-selector') as HTMLElement | null;
  fireEvent.mouseDown(trigger ?? control);
  const option = screen.getAllByText(optionText).find((node) => node.closest('.ant-select-item-option'))?.closest('.ant-select-item-option');
  expect(option).toBeDefined();
  fireEvent.click(option as HTMLElement);
}

describe('tenant-admin app', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    window.localStorage.clear();
  });

  afterEach(() => {
    cleanup();
  });

  it('renders login page by default', () => {
    render(<App />);
    expect(screen.getByText('登录租户管理台')).toBeInTheDocument();
  });

  it('submits chatbot embedding selection from the behavior form', async () => {
    seedSession();
    listChatbotsMock.mockResolvedValue([chatbotSummary]);
    listModelProvidersMock.mockResolvedValue(modelProviders);
    listModelDefinitionsMock.mockResolvedValue(modelDefinitions);
    getChatbotMock.mockResolvedValue(chatbotDetail);
    updateChatbotBehaviorMock.mockImplementation(async (_token, _chatbotId, request) => ({
      ...chatbotDetail,
      fallbackMessage: request.fallbackMessage,
      allowDirectModel: request.allowDirectModel,
      allowFeedback: request.allowFeedback,
      allowHandoff: request.allowHandoff,
      providerCode: request.providerCode ?? null,
      modelCode: request.modelCode ?? null,
      embeddingProviderCode: request.embeddingProviderCode ?? null,
      embeddingModelCode: request.embeddingModelCode ?? null
    }));

    render(<App />);

    expect(await screen.findByText('机器人管理')).toBeInTheDocument();
    fireEvent.click(await screen.findByRole('button', { name: 'Billing Bot' }));
    expect(await screen.findByText('知识 Embedding：系统默认 Embedding')).toBeInTheDocument();
    expect(await screen.findByRole('button', { name: '保存策略' })).toBeInTheDocument();

    selectFieldOption('Embedding Provider', 'Embedding Provider');
    selectFieldOption('Embedding Model', 'Embedding Model B');
    fireEvent.click(screen.getByRole('button', { name: '保存策略' }));

    await waitFor(() => {
      expect(updateChatbotBehaviorMock).toHaveBeenCalledWith('tenant-session-token', 101, {
        fallbackMessage: '暂时无法回答',
        allowDirectModel: true,
        allowFeedback: true,
        allowHandoff: true,
        providerCode: 'chat-provider',
        modelCode: 'chat-model-a',
        embeddingProviderCode: 'embed-provider',
        embeddingModelCode: 'embedding-model-b'
      });
    });

    expect(await screen.findByText('知识 Embedding：embed-provider / embedding-model-b')).toBeInTheDocument();
  });
});
