import { render, screen, waitFor } from '@testing-library/react';
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
    vi.unstubAllGlobals();
    vi.clearAllMocks();
  });

  it('renders welcome message from init api', async () => {
    render(<ChatSurface title="Chat" subtitle="subtitle" chatbotPublicCode="demo-bot" entryType="CHAT_PAGE" />);
    expect(screen.getByText('发送')).toBeInTheDocument();
    await waitFor(() => expect(screen.getByText('欢迎来到 AgentX。')).toBeInTheDocument());
    expect(fetchMock).toHaveBeenCalledTimes(1);
  });
});
