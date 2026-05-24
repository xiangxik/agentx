import { render, screen, waitFor } from '@testing-library/react';
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
    vi.unstubAllGlobals();
    vi.clearAllMocks();
  });

  it('renders title', async () => {
    render(<App />);
    expect(screen.getByText('独立聊天页')).toBeInTheDocument();
    await waitFor(() => expect(fetchMock).toHaveBeenCalledTimes(1));
  });
});
