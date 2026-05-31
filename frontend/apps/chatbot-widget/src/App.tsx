import { ChatSurface, resolvePublicChatConfig } from '@agentx/chat-core';

export function App() {
  const config = resolvePublicChatConfig({
    entryType: 'WIDGET',
    fallbackBot: 'demo-bot'
  });

  return (
    <ChatSurface
      title={config.title}
      subtitle={config.subtitle}
      chatbotPublicCode={config.chatbotPublicCode}
      entryType="WIDGET"
      apiBaseUrl={config.apiBaseUrl}
    />
  );
}
