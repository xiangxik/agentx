import { ChatSurface } from '@agentx/chat-core';

export function App() {
  const chatbotPublicCode = new URLSearchParams(globalThis.location.search).get('bot') ?? 'demo-bot';

  return (
    <ChatSurface
      title="AgentX 智能接待中心"
      subtitle="面向官网与活动页的正式访客入口，支持 FAQ、知识库检索与人工协助承接。"
      chatbotPublicCode={chatbotPublicCode}
      entryType="CHAT_PAGE"
    />
  );
}
