import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import { ChatSurface } from './index';

describe('chat-core', () => {
  const fetchMock = vi.fn();
  let sockets: MockWebSocket[] = [];

  class MockWebSocket {
    static readonly CONNECTING = 0;
    static readonly OPEN = 1;
    static readonly CLOSING = 2;
    static readonly CLOSED = 3;

    readonly url: string;
    readyState = MockWebSocket.CONNECTING;
    sent: string[] = [];
    onopen: (() => void) | null = null;
    onmessage: ((event: { data: string }) => void) | null = null;
    onerror: (() => void) | null = null;
    onclose: (() => void) | null = null;

    constructor(url: string) {
      this.url = url;
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
    sockets = [];
    fetchMock.mockResolvedValue({
      ok: true,
      json: async () => ({
        conversationId: 123,
        anonymousVisitorId: 'visitor-1',
        welcomeMessage: '欢迎来到 AgentX。',
        themeColor: '#2563eb',
        brandVisible: true,
        stylePreset: 'executive'
      })
    });
  });

  afterEach(() => {
    cleanup();
    vi.unstubAllGlobals();
    vi.clearAllMocks();
  });

  it('renders welcome message from init api', async () => {
    render(<ChatSurface title="Chat" subtitle="subtitle" chatbotPublicCode="demo-bot" entryType="CHAT_PAGE" />);
    await waitFor(() => expect(screen.getByText('欢迎来到 AgentX。')).toBeInTheDocument());
    expect(fetchMock).toHaveBeenCalledTimes(1);
    expect(sockets[0]?.url).toContain('/ws/public/chat');
  });

  it('renders assistant citations after websocket send', async () => {
    render(<ChatSurface title="Chat" subtitle="subtitle" chatbotPublicCode="demo-bot" entryType="CHAT_PAGE" />);

    await waitFor(() => expect(screen.getByText('欢迎来到 AgentX。')).toBeInTheDocument());
    expect(screen.getByText('退款规则')).toBeInTheDocument();
    sockets[0].open();
    await waitFor(() => expect(screen.getAllByText('已连接').length).toBeGreaterThan(0));

    fireEvent.change(screen.getByRole('textbox'), {
      target: { value: '退款规则是什么？' }
    });
    expect(screen.queryByText('退款规则')).not.toBeInTheDocument();
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
      answer: '根据知识库内容：退款规则为七天内可退款。',
      sourceType: 'KNOWLEDGE',
      citations: [
        {
          sourceId: 9,
          title: '退款政策',
          sourceType: 'KNOWLEDGE',
          sourceLink: 'https://example.com/refund-policy'
        }
      ],
      errorMessage: null
    });

    await waitFor(() => expect(screen.getByText('根据知识库内容：退款规则为七天内可退款。')).toBeInTheDocument());
    expect(screen.getByText('引用来源')).toBeInTheDocument();
    expect(screen.getByText('知识库: 退款政策')).toBeInTheDocument();
    expect(screen.getByRole('link', { name: /知识库: 退款政策/ })).toHaveAttribute('href', 'https://example.com/refund-policy');
  });

  it('applies style preset metadata from init api', async () => {
    fetchMock.mockResolvedValueOnce({
      ok: true,
      json: async () => ({
        conversationId: 123,
        anonymousVisitorId: 'visitor-1',
        welcomeMessage: '欢迎来到 AgentX。',
        themeColor: '#166534',
        brandVisible: false,
        stylePreset: 'forest'
      })
    });

    render(<ChatSurface title="Chat" subtitle="subtitle" chatbotPublicCode="demo-bot" entryType="CHAT_PAGE" />);

    await waitFor(() => expect(screen.getByText('欢迎来到 AgentX。')).toBeInTheDocument());
    expect(screen.queryByText(/当前风格：/)).not.toBeInTheDocument();
    expect(screen.queryByText('AgentX Live Chat')).not.toBeInTheDocument();
  });
});
