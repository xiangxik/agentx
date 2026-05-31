import { createRoot } from 'react-dom/client';
import { ChatSurface, resolvePublicChatConfig } from '@agentx/chat-core';
import React from 'react';

export interface AgentXChatbotInitOptions {
  target: string | HTMLElement;
  bot: string;
  title?: string;
  subtitle?: string;
  apiBaseUrl?: string;
  entryType?: 'CHAT_PAGE' | 'WIDGET';
}

declare global {
  interface Window {
    AgentXChatbot?: {
      init: (options: AgentXChatbotInitOptions) => void;
    };
  }
}

function mountChatbot(options: AgentXChatbotInitOptions) {
  const targetEl =
    typeof options.target === 'string'
      ? document.querySelector(options.target)
      : options.target;
  if (!targetEl) {
    throw new Error('AgentXChatbot: target element not found');
  }
  const config = {
    chatbotPublicCode: options.bot,
    title: options.title,
    subtitle: options.subtitle,
    apiBaseUrl: options.apiBaseUrl,
    entryType: options.entryType ?? 'WIDGET',
  };
  createRoot(targetEl).render(
    <React.StrictMode>
      <ChatSurface {...config} />
    </React.StrictMode>
  );
}

window.AgentXChatbot = {
  init: mountChatbot,
};
