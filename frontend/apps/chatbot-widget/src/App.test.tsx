import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import { App } from './App';

describe('chatbot-widget app', () => {
  const fetchMock = vi.fn();
  const sockets: MockWebSocket[] = [];

  class MockWebSocket {
    static readonly CONNECTING = 0;
    static readonly OPEN = 1;
    static readonly CLOSING = 2;
    static readonly CLOSED = 3;

    readyState = MockWebSocket.CONNECTING;
    sent: string[] = [];
    onopen: (() => void) | null = null;
    onmessage: ((event: { data: string }) => void) | null = null;
    onerror: (() => void) | null = null;
    onclose: (() => void) | null = null;

    constructor(public readonly url: string) {
      sockets.push(this);
    }

    send(payload: string) {
      this.sent.push(payload);
    }

    close() {
      this.readyState = MockWebSocket.CLOSED;
      this.onclose?.();
    }

    open() {
      this.readyState = MockWebSocket.OPEN;
      this.onopen?.();
    }

    emitMessage(payload: unknown) {
      this.onmessage?.({ data: JSON.stringify(payload) });
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
            name: '插件接待 Bot',
            language: 'zh-CN',
            status: 'ACTIVE',
            publicCode: 'demo-bot',
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

  it('renders widget title', async () => {
    render(<App />);
    expect(await screen.findByLabelText(/插件接待 Bot 嵌入式组件 · zh-CN/)).toBeInTheDocument();
    await waitFor(() => expect(fetchMock).toHaveBeenCalledTimes(2));
    await waitFor(() => expect(sockets[0]?.url).toContain('/ws/public/chat'));
  });

  it('renders knowledge citation link after send', async () => {
    render(<App />);

    await waitFor(() => expect(screen.getByText('欢迎来到 AgentX。')).toBeInTheDocument());
    sockets[0].open();
    await waitFor(() => expect(screen.getAllByText('已连接').length).toBeGreaterThan(0));

    fireEvent.change(screen.getByRole('textbox'), {
      target: { value: '退款规则是什么？' }
    });
    fireEvent.click(screen.getByText('发送消息'));

    const outboundPayload = JSON.parse(sockets[0].sent[0]) as {
      clientMessageId: string;
      message: string;
    };

    expect(outboundPayload.message).toBe('退款规则是什么？');

    sockets[0].emitMessage({
      type: 'PROCESSING',
      conversationId: 123,
      clientMessageId: outboundPayload.clientMessageId,
      visitorMessageId: null,
      assistantMessageId: null,
      answer: null,
      sourceType: null,
      citations: [],
      errorMessage: null
    });

    sockets[0].emitMessage({
      type: 'MESSAGE_COMPLETED',
      conversationId: 123,
      clientMessageId: outboundPayload.clientMessageId,
      visitorMessageId: '321',
      assistantMessageId: 456,
      answer: '根据知识库内容：订单支付后七天内可以在线提交退款申请。',
      sourceType: 'KNOWLEDGE',
      citations: [
        {
          sourceId: 9,
          title: '帮助中心',
          sourceType: 'KNOWLEDGE',
          sourceLink: 'https://example.com/help'
        }
      ],
      errorMessage: null
    });

    await waitFor(() => expect(screen.getByText('根据知识库内容：订单支付后七天内可以在线提交退款申请。')).toBeInTheDocument());
    expect(screen.getByRole('link', { name: /知识库: 帮助中心/ })).toHaveAttribute('href', 'https://example.com/help');
  });
});
