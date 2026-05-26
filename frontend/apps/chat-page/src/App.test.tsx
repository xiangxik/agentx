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
    fetchMock.mockResolvedValue({
      ok: true,
      json: async () => ({
        conversationId: 123,
        anonymousVisitorId: 'visitor-1',
        welcomeMessage: '欢迎来到 AgentX。',
        themeColor: '#2563eb'
      })
    });
  });

  afterEach(() => {
    cleanup();
    vi.unstubAllGlobals();
    vi.clearAllMocks();
  });

  it('renders title', async () => {
    render(<App />);
    expect(screen.getByLabelText(/AgentX 智能接待中心/)).toBeInTheDocument();
    await waitFor(() => expect(screen.getByText('欢迎来到 AgentX。')).toBeInTheDocument());
    expect(fetchMock).toHaveBeenCalledTimes(1);
    expect(sockets[0].url).toContain('chatbotPublicCode=6370fe97-8eb7-4e23-83ea-66d4514c95c0');
  });
});
