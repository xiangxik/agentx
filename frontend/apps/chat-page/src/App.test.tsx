import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import { App } from './App';

describe('chat-page app', () => {
  const fetchMock = vi.fn();

  beforeEach(() => {
    vi.stubGlobal('fetch', fetchMock);
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
    expect(screen.getByText('独立聊天页')).toBeInTheDocument();
    await waitFor(() => expect(fetchMock).toHaveBeenCalledTimes(1));
  });

  it('renders knowledge citation link after send', async () => {
    fetchMock
      .mockResolvedValueOnce({
        ok: true,
        json: async () => ({
          conversationId: 123,
          anonymousVisitorId: 'visitor-1',
          welcomeMessage: '欢迎来到 AgentX。',
          themeColor: '#2563eb'
        })
      })
      .mockResolvedValueOnce({
        ok: true,
        json: async () => ({
          conversationId: 123,
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
          ]
        })
      });

    render(<App />);

    await waitFor(() => expect(screen.getByText('欢迎来到 AgentX。')).toBeInTheDocument());

    fireEvent.change(screen.getByPlaceholderText('输入访客问题'), {
      target: { value: '退款规则是什么？' }
    });
    fireEvent.click(screen.getByText('发送'));

    await waitFor(() => expect(screen.getByText('根据知识库内容：订单支付后七天内可以在线提交退款申请。')).toBeInTheDocument());
    expect(screen.getByRole('link', { name: '知识库: 帮助中心' })).toHaveAttribute('href', 'https://example.com/help');
  });
});
