import { ChatSurface } from '@agentx/chat-core';

export function App() {
  const chatbotPublicCode = new URLSearchParams(globalThis.location.search).get('bot') ?? 'demo-bot';

  return (
    <ChatSurface
      title="独立聊天页"
      subtitle="共享 chatbot 配置快照的独立访客入口。"
      chatbotPublicCode={chatbotPublicCode}
      entryType="CHAT_PAGE"
    />
  );
}
