import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import { App } from './App';

describe('chat-page app', () => {
  const fetchMock = vi.fn();
  const sockets: MockWebSocket[] = [];

  class MockWebSocket {
    static readonly CONNECTING = 0;
    static readonly OPEN = 1;
    static readonly CLOSING = 2;
    static readonly CLOSED = 3;

    readyState = MockWebSocket.CONNECTING;
    onopen: (() => void) | null = null;
    onmessage: ((event: { data: string }) => void) | null = null;
    onerror: (() => void) | null = null;
    onclose: (() => void) | null = null;

    constructor(public readonly url: string) {
      sockets.push(this);
    }

    send() {}

    close() {
      this.readyState = MockWebSocket.CLOSED;
      this.onclose?.();
    }

    open() {
      this.readyState = MockWebSocket.OPEN;
      this.onopen?.();
    }
  }

  beforeEach(() => {
    vi.stubGlobal('fetch', fetchMock);
    vi.stubGlobal('WebSocket', MockWebSocket as unknown as typeof WebSocket);
    sockets.length = 0;
    window.history.pushState({}, '', '/');
    fetchMock.mockImplementation(async (input: string | URL | Request) => {
      const url = String(input);
      if (url.includes('/api/public/chatbots/')) {
        return {
          ok: true,
          json: async () => ({
            chatbotId: 101,
            tenantId: 7,
            name: '官网接待 Bot',
            language: 'zh-CN',
            status: 'ACTIVE',
            publicCode: '6370fe97-8eb7-4e23-83ea-66d4514c95c0',
            themeColor: '#2563eb',
            welcomeMessage: '欢迎来到 AgentX。',
            brandVisible: true,
            stylePreset: 'executive'
          })
        };
      }

      return {
        ok: true,
        json: async () => ({
          conversationId: 123,
          anonymousVisitorId: 'visitor-1',
          welcomeMessage: '欢迎来到 AgentX。',
          themeColor: '#2563eb',
          brandVisible: true,
          stylePreset: 'executive'
        })
      };
    });
  });

  afterEach(() => {
    cleanup();
    vi.unstubAllGlobals();
    vi.clearAllMocks();
  });

  it('renders title', async () => {
    render(<App />);
    expect(await screen.findByLabelText(/官网接待 Bot 公开聊天页 · zh-CN/)).toBeInTheDocument();
    await waitFor(() => expect(screen.getByText('欢迎来到 AgentX。')).toBeInTheDocument());
    expect(fetchMock).toHaveBeenCalledTimes(2);
    expect(sockets[0].url).toContain('chatbotPublicCode=6370fe97-8eb7-4e23-83ea-66d4514c95c0');
  });

  it('supports standardized global init config', async () => {
    (globalThis as typeof globalThis & { __AGENTX_CHAT_INIT__?: Record<string, string> }).__AGENTX_CHAT_INIT__ = {
      bot: 'global-bot',
      title: 'Global Chat',
      subtitle: 'Global Subtitle',
      apiBaseUrl: 'https://api.agentx.test'
    };

    fetchMock.mockImplementation(async (input: string | URL | Request) => {
      const url = String(input);
      if (url.includes('/api/public/chatbots/global-bot/snapshot')) {
        return {
          ok: true,
          json: async () => ({
            chatbotId: 101,
            tenantId: 7,
            name: 'Global Bot',
            language: 'zh-CN',
            status: 'ACTIVE',
            publicCode: 'global-bot',
            themeColor: '#2563eb',
            welcomeMessage: '欢迎来到 AgentX。',
            brandVisible: true,
            stylePreset: 'executive'
          })
        };
      }

      return {
        ok: true,
        json: async () => ({
          conversationId: 123,
          anonymousVisitorId: 'visitor-1',
          welcomeMessage: '欢迎来到 AgentX。',
          themeColor: '#2563eb',
          brandVisible: true,
          stylePreset: 'executive'
        })
      };
    });

    render(<App />);

    expect(await screen.findByLabelText(/Global Chat Global Subtitle/)).toBeInTheDocument();
    await waitFor(() => expect(fetchMock).toHaveBeenCalledWith('https://api.agentx.test/api/public/chatbots/global-bot/snapshot', expect.any(Object)));
    await waitFor(() => expect(sockets[0].url).toContain('wss://api.agentx.test/ws/public/chat'));

    delete (globalThis as typeof globalThis & { __AGENTX_CHAT_INIT__?: Record<string, string> }).__AGENTX_CHAT_INIT__;
  });
});
