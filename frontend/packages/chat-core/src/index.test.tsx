import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import { ChatSurface } from './index';

describe('chat-core', () => {
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

  it('renders welcome message from init api', async () => {
    render(<ChatSurface title="Chat" subtitle="subtitle" chatbotPublicCode="demo-bot" entryType="CHAT_PAGE" />);
    expect(screen.getByText('发送')).toBeInTheDocument();
    await waitFor(() => expect(screen.getByText('欢迎来到 AgentX。')).toBeInTheDocument());
    expect(fetchMock).toHaveBeenCalledTimes(1);
  });

  it('renders assistant citations after send', async () => {
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
          answer: '根据知识库内容：退款规则为七天内可退款。',
          sourceType: 'KNOWLEDGE',
          citations: [
            {
              sourceId: 9,
              title: '退款政策',
              sourceType: 'KNOWLEDGE',
              sourceLink: 'https://example.com/refund-policy'
            }
          ]
        })
      });

    render(<ChatSurface title="Chat" subtitle="subtitle" chatbotPublicCode="demo-bot" entryType="CHAT_PAGE" />);

    await waitFor(() => expect(screen.getByText('欢迎来到 AgentX。')).toBeInTheDocument());

    fireEvent.change(screen.getByPlaceholderText('输入访客问题'), {
      target: { value: '退款规则是什么？' }
    });
    fireEvent.click(screen.getByText('发送'));

    await waitFor(() => expect(screen.getByText('根据知识库内容：退款规则为七天内可退款。')).toBeInTheDocument());
    expect(screen.getByText('引用来源')).toBeInTheDocument();
    expect(screen.getByText('知识库: 退款政策')).toBeInTheDocument();
    expect(screen.getByRole('link', { name: '知识库: 退款政策' })).toHaveAttribute('href', 'https://example.com/refund-policy');
  });
});
