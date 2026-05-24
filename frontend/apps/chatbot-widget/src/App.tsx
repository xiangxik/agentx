import { ChatSurface } from '@agentx/chat-core';

export function App() {
  const chatbotPublicCode = new URLSearchParams(globalThis.location.search).get('bot') ?? 'demo-bot';

  return (
    <ChatSurface
      title="网站插件"
      subtitle="后续会通过嵌入脚本 + 域名白名单初始化。"
      chatbotPublicCode={chatbotPublicCode}
      entryType="WIDGET"
    />
  );
}
