import { useEffect, useState } from 'react';

export interface ChatMessage {
  id: string;
  role: 'visitor' | 'assistant';
  content: string;
  sourceType?: string;
  citations?: Array<{ sourceId: number; title: string; sourceType: string; sourceLink?: string | null }>;
}

interface ChatSurfaceProps {
  title: string;
  subtitle: string;
  chatbotPublicCode: string;
  entryType: 'CHAT_PAGE' | 'WIDGET';
}

interface InitResponse {
  conversationId: number;
  anonymousVisitorId: string;
  welcomeMessage: string;
  themeColor: string;
}

interface SendResponse {
  conversationId: number;
  assistantMessageId: number;
  answer: string;
  sourceType: string;
  citations: Array<{ sourceId: number; title: string; sourceType: string; sourceLink?: string | null }>;
}

interface ApiErrorResponse {
  code?: string;
}

const resolveApiBaseUrl = () => (globalThis as typeof globalThis & { __AGENTX_API_BASE_URL__?: string }).__AGENTX_API_BASE_URL__ ?? 'http://localhost:8080';

function mapChatError(code: string | undefined, fallback: string) {
  switch (code) {
    case 'CONVERSATIONS_LIMIT_REACHED':
      return '当前租户的会话额度已达上限，暂时无法创建新会话。';
    case 'MESSAGES_LIMIT_REACHED':
      return '当前租户的消息额度已达上限，暂时无法继续发送。';
    case 'CHATBOT_NOT_ACTIVE':
      return '当前 Chatbot 未启用，暂时无法对外提供服务。';
    default:
      return fallback;
  }
}

export function ChatSurface({ title, subtitle, chatbotPublicCode, entryType }: ChatSurfaceProps) {
  const [messages, setMessages] = useState<ChatMessage[]>([
    {
      id: 'welcome',
      role: 'assistant',
      content: '你好，这里是机器人欢迎语占位。'
    }
  ]);
  const [draft, setDraft] = useState('');
  const [conversationId, setConversationId] = useState<number | null>(null);
  const [isLoading, setIsLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    let cancelled = false;
    const controller = new AbortController();

    const initConversation = async () => {
      try {
        const response = await fetch(`${resolveApiBaseUrl()}/api/public/chat/init`, {
          method: 'POST',
          signal: controller.signal,
          headers: {
            'Content-Type': 'application/json'
          },
          body: JSON.stringify({
            chatbotPublicCode,
            entryType,
            domain: globalThis.location?.hostname ?? 'localhost',
            ipAddress: '127.0.0.1',
            userAgent: globalThis.navigator?.userAgent ?? 'browser'
          })
        });

        if (!response.ok) {
          const responseBody = (await response.json().catch(() => null)) as ApiErrorResponse | null;
          throw new Error(
            mapChatError(responseBody?.code, `Failed to initialize conversation: ${response.status}`)
          );
        }

        const payload = (await response.json()) as InitResponse;

        if (!cancelled) {
          setConversationId(payload.conversationId);
          setMessages([
            {
              id: 'welcome',
              role: 'assistant',
              content: payload.welcomeMessage
            }
          ]);
        }
      } catch (initError) {
        if (initError instanceof DOMException && initError.name === 'AbortError') {
          return;
        }

        if (!cancelled) {
          setError(initError instanceof Error ? initError.message : '初始化会话失败');
        }
      }
    };

    void initConversation();

    return () => {
      cancelled = true;
      controller.abort();
    };
  }, [chatbotPublicCode, entryType]);

  return (
    <div style={{ maxWidth: 900, margin: '0 auto', padding: 24, fontFamily: 'sans-serif' }}>
      <header style={{ marginBottom: 24 }}>
        <h1 style={{ marginBottom: 8 }}>{title}</h1>
        <p style={{ color: '#6b7280' }}>{subtitle}</p>
        {error ? <p style={{ color: '#dc2626' }}>{error}</p> : null}
      </header>
      <section
        style={{
          minHeight: 420,
          background: '#fff',
          borderRadius: 20,
          border: '1px solid #e5e7eb',
          padding: 16,
          display: 'grid',
          gap: 12,
          marginBottom: 16
        }}
      >
        {messages.map((message) => (
          <div
            key={message.id}
            style={{
              justifySelf: message.role === 'visitor' ? 'end' : 'start',
              background: message.role === 'visitor' ? '#2563eb' : '#f3f4f6',
              color: message.role === 'visitor' ? '#fff' : '#111827',
              padding: '10px 14px',
              borderRadius: 14,
              maxWidth: '75%'
            }}
          >
            <div>{message.content}</div>
            {message.role === 'assistant' && message.citations && message.citations.length > 0 ? (
              <div style={{ display: 'grid', gap: 6, marginTop: 10 }}>
                <div style={{ fontSize: 12, color: '#64748b' }}>引用来源</div>
                <div style={{ display: 'flex', gap: 8, flexWrap: 'wrap' }}>
                  {message.citations.map((citation) => (
                    <a
                      key={`${message.id}-${citation.sourceType}-${citation.sourceId}`}
                      href={citation.sourceLink ?? undefined}
                      target={citation.sourceLink ? '_blank' : undefined}
                      rel={citation.sourceLink ? 'noreferrer' : undefined}
                      style={{
                        display: 'inline-flex',
                        alignItems: 'center',
                        borderRadius: 999,
                        background: '#e2e8f0',
                        color: '#0f172a',
                        fontSize: 12,
                        padding: '4px 10px',
                        textDecoration: 'none',
                        cursor: citation.sourceLink ? 'pointer' : 'default'
                      }}
                    >
                      {citation.sourceType === 'KNOWLEDGE' ? '知识库' : citation.sourceType}: {citation.title}
                    </a>
                  ))}
                </div>
              </div>
            ) : null}
          </div>
        ))}
      </section>
      <form
        onSubmit={(event) => {
          event.preventDefault();
          if (!draft.trim() || !conversationId || isLoading) {
            return;
          }

          const nextDraft = draft.trim();
          setMessages((current) => [...current, { id: crypto.randomUUID(), role: 'visitor', content: nextDraft }]);
          setDraft('');
          setIsLoading(true);
          setError(null);

          void fetch(`${resolveApiBaseUrl()}/api/public/chat/messages`, {
            method: 'POST',
            headers: {
              'Content-Type': 'application/json'
            },
            body: JSON.stringify({
              conversationId,
              chatbotPublicCode,
              language: 'zh-CN',
              message: nextDraft
            })
          })
            .then(async (response) => {
              if (!response.ok) {
                const responseBody = (await response.json().catch(() => null)) as ApiErrorResponse | null;
                throw new Error(mapChatError(responseBody?.code, `Failed to send message: ${response.status}`));
              }
              return (await response.json()) as SendResponse;
            })
            .then((payload) => {
              setMessages((current) => [
                ...current,
                {
                  id: String(payload.assistantMessageId),
                  role: 'assistant',
                  content: payload.answer,
                  sourceType: payload.sourceType,
                  citations: payload.citations
                }
              ]);
            })
            .catch((sendError: unknown) => {
              setError(sendError instanceof Error ? sendError.message : '消息发送失败');
            })
            .finally(() => {
              setIsLoading(false);
            });
        }}
        style={{ display: 'grid', gridTemplateColumns: '1fr auto', gap: 12 }}
      >
        <input
          value={draft}
          onChange={(event) => setDraft(event.target.value)}
          placeholder="输入访客问题"
          style={{ borderRadius: 12, border: '1px solid #d1d5db', padding: 14 }}
        />
        <button
          type="submit"
          style={{
            borderRadius: 12,
            border: 'none',
            background: '#111827',
            color: '#fff',
            padding: '0 18px'
          }}
        >
          {isLoading ? '发送中...' : '发送'}
        </button>
      </form>
    </div>
  );
}
