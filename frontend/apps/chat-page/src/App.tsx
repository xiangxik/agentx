import { ChatSurface, resolvePublicChatConfig } from '@agentx/chat-core';

export function App() {
  const config = resolvePublicChatConfig({
    entryType: 'CHAT_PAGE',
    fallbackBot: '6370fe97-8eb7-4e23-83ea-66d4514c95c0'
  });

  return (
    <ChatSurface
      title={config.title}
      subtitle={config.subtitle}
      chatbotPublicCode={config.chatbotPublicCode}
      entryType="CHAT_PAGE"
      apiBaseUrl={config.apiBaseUrl}
    />
  );
}
