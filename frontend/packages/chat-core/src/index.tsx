import { useEffect, useRef, useState } from 'react';

type MessageStatus = 'sent' | 'pending' | 'failed';
type ConnectionStatus = 'idle' | 'connecting' | 'connected' | 'disconnected' | 'failed';
type StylePreset = 'executive' | 'slate' | 'heritage' | 'forest' | 'graphite';

export interface ChatMessage {
  id: string;
  role: 'visitor' | 'assistant';
  content: string;
  createdAt: string;
  status?: MessageStatus;
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
  brandVisible?: boolean;
  stylePreset?: string;
}

interface ApiErrorResponse {
  code?: string;
}

interface ChatServerEvent {
  type: 'CONNECTED' | 'PROCESSING' | 'MESSAGE_COMPLETED' | 'ERROR';
  conversationId: number | null;
  clientMessageId: string | null;
  visitorMessageId: string | null;
  assistantMessageId: number | null;
  citations: Array<{ sourceId: number; title: string; sourceType: string; sourceLink?: string | null }>;
  answer: string | null;
  sourceType: string | null;
  errorMessage: string | null;
}

interface SurfaceTheme {
  label: string;
  heroLabel: string;
  pageBackground: string;
  pageForeground: string;
  surfaceBackground: string;
  surfaceBorder: string;
  surfaceShadow: string;
  headerBackground: string;
  brandBadgeBackground: string;
  brandBadgeColor: string;
  transcriptBackground: string;
  assistantBubbleBackground: string;
  assistantBubbleBorder: string;
  assistantBubbleShadow: string;
  quickPromptBackground: string;
  quickPromptColor: string;
  composerBackground: string;
  composerBorder: string;
  composerShadow: string;
  submitButtonBackground: string;
  submitButtonDisabledBackground: string;
  asideHeroBackground: string;
  asideHeroAccent: string;
  asideHeroText: string;
  sideCardBackground: string;
  sideCardBorder: string;
  sideCardShadow: string;
  mutedText: string;
}

const QUICK_PROMPTS = ['退款规则', '在线时间', '人工协助'];
const DEFAULT_THEME_COLOR = '#2563eb';

const resolveApiBaseUrl = () =>
  (globalThis as typeof globalThis & { __AGENTX_API_BASE_URL__?: string }).__AGENTX_API_BASE_URL__ ??
  'http://localhost:8080';

function resolveWebSocketUrl(apiBaseUrl: string, chatbotPublicCode: string, conversationId: number) {
  const target = new URL(apiBaseUrl, globalThis.location?.origin ?? 'http://localhost:8080');
  target.protocol = target.protocol === 'https:' ? 'wss:' : 'ws:';
  target.pathname = '/ws/public/chat';
  target.search = new URLSearchParams({
    chatbotPublicCode,
    conversationId: String(conversationId)
  }).toString();
  return target.toString();
}

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

function formatClock(date = new Date()) {
  return new Intl.DateTimeFormat('zh-CN', {
    hour: '2-digit',
    minute: '2-digit'
  }).format(date);
}

function createClientMessageId() {
  return globalThis.crypto?.randomUUID?.() ?? `msg-${Date.now()}-${Math.random().toString(16).slice(2)}`;
}

function fade(hex: string, alpha: number) {
  const normalized = hex.replace('#', '');
  if (!/^[0-9a-fA-F]{6}$/.test(normalized)) {
    return `rgba(37, 99, 235, ${alpha})`;
  }

  const red = Number.parseInt(normalized.slice(0, 2), 16);
  const green = Number.parseInt(normalized.slice(2, 4), 16);
  const blue = Number.parseInt(normalized.slice(4, 6), 16);
  return `rgba(${red}, ${green}, ${blue}, ${alpha})`;
}

function normalizeStylePreset(stylePreset: string | undefined): StylePreset {
  switch (stylePreset) {
    case 'slate':
    case 'heritage':
    case 'forest':
    case 'graphite':
      return stylePreset;
    default:
      return 'executive';
  }
}

function resolveAccentColor(themeColor: string, fallbackColor: string) {
  return /^#[0-9a-fA-F]{6}$/.test(themeColor) ? themeColor : fallbackColor;
}

function resolveSurfaceTheme(stylePreset: StylePreset, themeColor: string): SurfaceTheme {
  switch (stylePreset) {
    case 'slate': {
      const accent = resolveAccentColor(themeColor, '#475569');
      return {
        label: 'Slate Ledger',
        heroLabel: 'Slate Edition',
        pageBackground: `radial-gradient(circle at top left, ${fade(accent, 0.22)}, transparent 32%), linear-gradient(145deg, #f4f4f5 0%, #eef2f7 45%, #e2e8f0 100%)`,
        pageForeground: '#111827',
        surfaceBackground: 'rgba(255, 255, 255, 0.84)',
        surfaceBorder: `1px solid ${fade(accent, 0.18)}`,
        surfaceShadow: '0 24px 60px rgba(30, 41, 59, 0.12)',
        headerBackground: `linear-gradient(135deg, ${fade(accent, 0.12)}, rgba(255,255,255,0.75))`,
        brandBadgeBackground: '#1f2937',
        brandBadgeColor: '#f8fafc',
        transcriptBackground: 'linear-gradient(180deg, rgba(255,255,255,0.56) 0%, rgba(248,250,252,0.9) 100%)',
        assistantBubbleBackground: '#f8fafc',
        assistantBubbleBorder: '1px solid rgba(100, 116, 139, 0.20)',
        assistantBubbleShadow: '0 12px 24px rgba(51, 65, 85, 0.08)',
        quickPromptBackground: '#f8fafc',
        quickPromptColor: '#0f172a',
        composerBackground: '#ffffff',
        composerBorder: `1px solid ${fade(accent, 0.18)}`,
        composerShadow: '0 18px 36px rgba(30, 41, 59, 0.08)',
        submitButtonBackground: '#1f2937',
        submitButtonDisabledBackground: '#cbd5e1',
        asideHeroBackground: 'linear-gradient(180deg, #111827 0%, #334155 100%)',
        asideHeroAccent: '#cbd5e1',
        asideHeroText: '#e2e8f0',
        sideCardBackground: 'rgba(255,255,255,0.9)',
        sideCardBorder: `1px solid ${fade(accent, 0.14)}`,
        sideCardShadow: '0 18px 40px rgba(30, 41, 59, 0.09)',
        mutedText: '#475569'
      };
    }
    case 'heritage': {
      const accent = resolveAccentColor(themeColor, '#8b1e3f');
      return {
        label: 'Heritage Reserve',
        heroLabel: 'Heritage Edition',
        pageBackground: `radial-gradient(circle at top left, ${fade(accent, 0.16)}, transparent 34%), linear-gradient(150deg, #faf7f2 0%, #f4ede2 44%, #ebe3d5 100%)`,
        pageForeground: '#2f241f',
        surfaceBackground: 'rgba(255, 252, 247, 0.88)',
        surfaceBorder: `1px solid ${fade(accent, 0.16)}`,
        surfaceShadow: '0 24px 60px rgba(68, 40, 32, 0.10)',
        headerBackground: `linear-gradient(135deg, ${fade(accent, 0.10)}, rgba(255,248,240,0.88))`,
        brandBadgeBackground: '#4a1f26',
        brandBadgeColor: '#f7efe7',
        transcriptBackground: 'linear-gradient(180deg, rgba(255,250,244,0.74) 0%, rgba(250,247,242,0.96) 100%)',
        assistantBubbleBackground: '#fffaf4',
        assistantBubbleBorder: '1px solid rgba(167, 139, 114, 0.22)',
        assistantBubbleShadow: '0 12px 22px rgba(91, 58, 48, 0.07)',
        quickPromptBackground: '#fffaf4',
        quickPromptColor: '#3f2d28',
        composerBackground: '#fffdf9',
        composerBorder: `1px solid ${fade(accent, 0.18)}`,
        composerShadow: '0 18px 34px rgba(91, 58, 48, 0.08)',
        submitButtonBackground: '#4a1f26',
        submitButtonDisabledBackground: '#d8c7bb',
        asideHeroBackground: 'linear-gradient(180deg, #3f1d25 0%, #6b2c39 100%)',
        asideHeroAccent: '#e7d3c3',
        asideHeroText: '#f6ede5',
        sideCardBackground: 'rgba(255,251,246,0.92)',
        sideCardBorder: `1px solid ${fade(accent, 0.14)}`,
        sideCardShadow: '0 18px 40px rgba(68, 40, 32, 0.08)',
        mutedText: '#6b5b53'
      };
    }
    case 'forest': {
      const accent = resolveAccentColor(themeColor, '#166534');
      return {
        label: 'Forest Council',
        heroLabel: 'Forest Edition',
        pageBackground: `radial-gradient(circle at top left, ${fade(accent, 0.18)}, transparent 34%), linear-gradient(145deg, #f3f6f2 0%, #edf3ee 44%, #dde8df 100%)`,
        pageForeground: '#15231b',
        surfaceBackground: 'rgba(252, 255, 252, 0.86)',
        surfaceBorder: `1px solid ${fade(accent, 0.16)}`,
        surfaceShadow: '0 24px 60px rgba(21, 35, 27, 0.10)',
        headerBackground: `linear-gradient(135deg, ${fade(accent, 0.10)}, rgba(255,255,255,0.78))`,
        brandBadgeBackground: '#16301f',
        brandBadgeColor: '#f0fdf4',
        transcriptBackground: 'linear-gradient(180deg, rgba(255,255,255,0.54) 0%, rgba(244,248,245,0.96) 100%)',
        assistantBubbleBackground: '#f8fbf8',
        assistantBubbleBorder: '1px solid rgba(110, 145, 118, 0.20)',
        assistantBubbleShadow: '0 12px 24px rgba(22, 48, 31, 0.07)',
        quickPromptBackground: '#f8fbf8',
        quickPromptColor: '#17301f',
        composerBackground: '#ffffff',
        composerBorder: `1px solid ${fade(accent, 0.18)}`,
        composerShadow: '0 18px 36px rgba(22, 48, 31, 0.08)',
        submitButtonBackground: '#17301f',
        submitButtonDisabledBackground: '#c7d7ca',
        asideHeroBackground: 'linear-gradient(180deg, #17301f 0%, #28553b 100%)',
        asideHeroAccent: '#bbf7d0',
        asideHeroText: '#dcfce7',
        sideCardBackground: 'rgba(252,255,252,0.92)',
        sideCardBorder: `1px solid ${fade(accent, 0.14)}`,
        sideCardShadow: '0 18px 40px rgba(22, 48, 31, 0.08)',
        mutedText: '#4b6355'
      };
    }
    case 'graphite': {
      const accent = resolveAccentColor(themeColor, '#0f172a');
      return {
        label: 'Graphite Boardroom',
        heroLabel: 'Graphite Edition',
        pageBackground: `radial-gradient(circle at top left, ${fade(accent, 0.12)}, transparent 34%), linear-gradient(150deg, #f5f7fa 0%, #eceff3 42%, #dde3ea 100%)`,
        pageForeground: '#0f172a',
        surfaceBackground: 'rgba(255, 255, 255, 0.86)',
        surfaceBorder: `1px solid ${fade(accent, 0.14)}`,
        surfaceShadow: '0 24px 60px rgba(15, 23, 42, 0.12)',
        headerBackground: `linear-gradient(135deg, ${fade(accent, 0.08)}, rgba(255,255,255,0.80))`,
        brandBadgeBackground: '#0f172a',
        brandBadgeColor: '#f8fafc',
        transcriptBackground: 'linear-gradient(180deg, rgba(255,255,255,0.58) 0%, rgba(243,246,249,0.96) 100%)',
        assistantBubbleBackground: '#f8fafc',
        assistantBubbleBorder: '1px solid rgba(71, 85, 105, 0.18)',
        assistantBubbleShadow: '0 12px 24px rgba(15, 23, 42, 0.07)',
        quickPromptBackground: '#f8fafc',
        quickPromptColor: '#0f172a',
        composerBackground: '#ffffff',
        composerBorder: `1px solid ${fade(accent, 0.16)}`,
        composerShadow: '0 18px 36px rgba(15, 23, 42, 0.08)',
        submitButtonBackground: '#111827',
        submitButtonDisabledBackground: '#cbd5e1',
        asideHeroBackground: 'linear-gradient(180deg, #020617 0%, #1e293b 100%)',
        asideHeroAccent: '#cbd5e1',
        asideHeroText: '#e2e8f0',
        sideCardBackground: 'rgba(255,255,255,0.92)',
        sideCardBorder: `1px solid ${fade(accent, 0.12)}`,
        sideCardShadow: '0 18px 40px rgba(15, 23, 42, 0.08)',
        mutedText: '#475569'
      };
    }
    default: {
      const accent = resolveAccentColor(themeColor, DEFAULT_THEME_COLOR);
      return {
        label: 'Executive Horizon',
        heroLabel: 'Executive Edition',
        pageBackground: `radial-gradient(circle at top left, ${fade(accent, 0.28)}, transparent 32%), linear-gradient(135deg, #f8fafc 0%, #fff8ef 46%, #eef6ff 100%)`,
        pageForeground: '#111827',
        surfaceBackground: 'rgba(255, 255, 255, 0.82)',
        surfaceBorder: `1px solid ${fade(accent, 0.18)}`,
        surfaceShadow: '0 24px 70px rgba(15, 23, 42, 0.10)',
        headerBackground: `linear-gradient(135deg, ${fade(accent, 0.14)}, rgba(255,255,255,0.7))`,
        brandBadgeBackground: '#111827',
        brandBadgeColor: '#fff',
        transcriptBackground: 'linear-gradient(180deg, rgba(255,255,255,0.48) 0%, rgba(255,255,255,0.86) 24%, rgba(248,250,252,0.98) 100%)',
        assistantBubbleBackground: '#ffffff',
        assistantBubbleBorder: '1px solid rgba(148,163,184,0.18)',
        assistantBubbleShadow: '0 12px 24px rgba(15, 23, 42, 0.06)',
        quickPromptBackground: '#fff',
        quickPromptColor: '#0f172a',
        composerBackground: '#fff',
        composerBorder: `1px solid ${fade(accent, 0.18)}`,
        composerShadow: '0 18px 36px rgba(15, 23, 42, 0.07)',
        submitButtonBackground: '#111827',
        submitButtonDisabledBackground: '#cbd5e1',
        asideHeroBackground: '#111827',
        asideHeroAccent: '#93c5fd',
        asideHeroText: '#cbd5e1',
        sideCardBackground: 'rgba(255,255,255,0.84)',
        sideCardBorder: `1px solid ${fade(accent, 0.16)}`,
        sideCardShadow: '0 18px 40px rgba(15, 23, 42, 0.08)',
        mutedText: '#475569'
      };
    }
  }
}

function connectionLabel(status: ConnectionStatus) {
  switch (status) {
    case 'connecting':
      return '连接中';
    case 'connected':
      return '已连接';
    case 'disconnected':
      return '连接已断开';
    case 'failed':
      return '连接失败';
    default:
      return '等待初始化';
  }
}

function connectionTone(status: ConnectionStatus, themeColor: string) {
  switch (status) {
    case 'connected':
      return {
        background: 'rgba(236, 253, 245, 0.92)',
        border: '1px solid rgba(34, 197, 94, 0.18)',
        color: '#166534',
        dot: '#16a34a',
        glow: '0 0 0 7px rgba(22, 163, 74, 0.12)'
      };
    case 'connecting':
      return {
        background: 'rgba(255, 251, 235, 0.94)',
        border: '1px solid rgba(245, 158, 11, 0.18)',
        color: '#b45309',
        dot: '#f59e0b',
        glow: '0 0 0 7px rgba(245, 158, 11, 0.12)'
      };
    case 'failed':
    case 'disconnected':
      return {
        background: 'rgba(254, 242, 242, 0.94)',
        border: '1px solid rgba(239, 68, 68, 0.18)',
        color: '#b91c1c',
        dot: '#ef4444',
        glow: '0 0 0 7px rgba(239, 68, 68, 0.12)'
      };
    default:
      return {
        background: fade(themeColor, 0.08),
        border: `1px solid ${fade(themeColor, 0.16)}`,
        color: '#334155',
        dot: themeColor,
        glow: `0 0 0 7px ${fade(themeColor, 0.12)}`
      };
  }
}

function sourceTypeLabel(sourceType: string | undefined) {
  if (sourceType === 'KNOWLEDGE') {
    return '知识库';
  }

  if (sourceType === 'FAQ') {
    return 'FAQ';
  }

  return sourceType ?? '系统';
}

function AssistantAvatar() {
  return (
    <span
      aria-hidden="true"
      style={{
        width: 30,
        height: 30,
        borderRadius: '50%',
        display: 'inline-flex',
        alignItems: 'center',
        justifyContent: 'center',
        background: '#e2e8f0',
        color: '#0f172a',
        flexShrink: 0
      }}
    >
      <svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round">
        <path d="M12 3 4 7v6c0 5 3.4 7.8 8 8 4.6-.2 8-3 8-8V7l-8-4Z" />
        <path d="M9.5 12h5" />
        <path d="M12 9.5v5" />
      </svg>
    </span>
  );
}

function VisitorAvatar({ themeColor }: { themeColor: string }) {
  return (
    <span
      aria-hidden="true"
      style={{
        width: 30,
        height: 30,
        borderRadius: '50%',
        display: 'inline-flex',
        alignItems: 'center',
        justifyContent: 'center',
        background: fade(themeColor, 0.18),
        color: themeColor,
        flexShrink: 0
      }}
    >
      <svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round">
        <path d="M12 12a3.5 3.5 0 1 0 0-7 3.5 3.5 0 0 0 0 7Z" />
        <path d="M5 20a7 7 0 0 1 14 0" />
      </svg>
    </span>
  );
}

export function ChatSurface({ title, subtitle, chatbotPublicCode, entryType }: ChatSurfaceProps) {
  const [messages, setMessages] = useState<ChatMessage[]>([]);
  const [draft, setDraft] = useState('');
  const [conversationId, setConversationId] = useState<number | null>(null);
  const [anonymousVisitorId, setAnonymousVisitorId] = useState('');
  const [themeColor, setThemeColor] = useState(DEFAULT_THEME_COLOR);
  const [, setBrandVisible] = useState(true);
  const [stylePreset, setStylePreset] = useState<StylePreset>('executive');
  const [isInitializing, setIsInitializing] = useState(true);
  const [isLoading, setIsLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [connectionStatus, setConnectionStatus] = useState<ConnectionStatus>('idle');
  const [retrySeed, setRetrySeed] = useState(0);
  const [viewportWidth, setViewportWidth] = useState(() => globalThis.innerWidth || 1280);
  const socketRef = useRef<WebSocket | null>(null);
  const reconnectTimerRef = useRef<number | null>(null);
  const transcriptRef = useRef<HTMLDivElement | null>(null);
  const theme = resolveSurfaceTheme(stylePreset, themeColor);
  const connectionAppearance = connectionTone(connectionStatus, themeColor);
  const isCompact = viewportWidth < 1080;
  const isPhone = viewportWidth < 720;

  useEffect(() => {
    const handleResize = () => setViewportWidth(globalThis.innerWidth || 1280);
    handleResize();
    globalThis.addEventListener('resize', handleResize);
    return () => globalThis.removeEventListener('resize', handleResize);
  }, []);

  useEffect(() => {
    if (transcriptRef.current && typeof transcriptRef.current.scrollTo === 'function') {
      transcriptRef.current.scrollTo({ top: transcriptRef.current.scrollHeight, behavior: 'smooth' });
    }
  }, [messages, isLoading]);

  useEffect(() => {
    let cancelled = false;
    const controller = new AbortController();

    setIsInitializing(true);
    setError(null);
    setMessages([]);
    setConversationId(null);
    setAnonymousVisitorId('');
    setThemeColor(DEFAULT_THEME_COLOR);
    setBrandVisible(true);
    setStylePreset('executive');
    setConnectionStatus('idle');

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

        if (cancelled) {
          return;
        }

        setConversationId(payload.conversationId);
        setAnonymousVisitorId(payload.anonymousVisitorId);
  setThemeColor(payload.themeColor || DEFAULT_THEME_COLOR);
  setBrandVisible(payload.brandVisible ?? true);
  setStylePreset(normalizeStylePreset(payload.stylePreset));
        setMessages([
          {
            id: 'welcome',
            role: 'assistant',
            content: payload.welcomeMessage,
            createdAt: formatClock()
          }
        ]);
      } catch (initError) {
        if (initError instanceof DOMException && initError.name === 'AbortError') {
          return;
        }

        if (!cancelled) {
          setError(initError instanceof Error ? initError.message : '初始化会话失败');
        }
      } finally {
        if (!cancelled) {
          setIsInitializing(false);
        }
      }
    };

    void initConversation();

    return () => {
      cancelled = true;
      controller.abort();
    };
  }, [chatbotPublicCode, entryType]);

  useEffect(() => {
    if (!conversationId) {
      return;
    }

    let disposed = false;
    setConnectionStatus('connecting');

    const socket = new WebSocket(resolveWebSocketUrl(resolveApiBaseUrl(), chatbotPublicCode, conversationId));
    socketRef.current = socket;

    socket.onopen = () => {
      if (!disposed) {
        setConnectionStatus('connected');
        setError(null);
      }
    };

    socket.onmessage = (event) => {
      const payload = JSON.parse(String(event.data)) as ChatServerEvent;

      if (payload.type === 'CONNECTED') {
        setConnectionStatus('connected');
        return;
      }

      if (payload.type === 'PROCESSING') {
        setIsLoading(true);
        return;
      }

      if (payload.type === 'ERROR') {
        setIsLoading(false);
        setError(payload.errorMessage ?? '消息发送失败');
        setMessages((current) =>
          current.map((message) =>
            message.id === payload.clientMessageId ? { ...message, status: 'failed' } : message
          )
        );
        return;
      }

      setIsLoading(false);
      setError(null);
      setMessages((current) => {
        const next = current.map((message) =>
          message.id === payload.clientMessageId
            ? {
                ...message,
                id: payload.clientMessageId ?? message.id,
                status: 'sent'
              }
            : message
        );

        return [
          ...next,
          {
            id: String(payload.assistantMessageId ?? createClientMessageId()),
            role: 'assistant',
            content: payload.answer ?? '',
            createdAt: formatClock(),
            sourceType: payload.sourceType ?? undefined,
            citations: payload.citations
          }
        ];
      });
    };

    socket.onerror = () => {
      if (!disposed) {
        setConnectionStatus('failed');
      }
    };

    socket.onclose = () => {
      if (socketRef.current === socket) {
        socketRef.current = null;
      }

      if (disposed) {
        return;
      }

      setConnectionStatus('disconnected');
      setIsLoading(false);
      setMessages((current) =>
        current.map((message) =>
          message.status === 'pending' ? { ...message, status: 'failed' } : message
        )
      );

      reconnectTimerRef.current = globalThis.setTimeout(() => {
        setRetrySeed((current) => current + 1);
      }, 1200);
    };

    return () => {
      disposed = true;
      if (reconnectTimerRef.current) {
        globalThis.clearTimeout(reconnectTimerRef.current);
      }
      if (socket.readyState === WebSocket.OPEN || socket.readyState === WebSocket.CONNECTING) {
        socket.close();
      }
      if (socketRef.current === socket) {
        socketRef.current = null;
      }
    };
  }, [chatbotPublicCode, conversationId, retrySeed]);

  const canSend =
    Boolean(draft.trim()) &&
    Boolean(conversationId) &&
    !isInitializing &&
    !isLoading &&
    connectionStatus === 'connected';

  const submitMessage = (content: string, messageId = createClientMessageId()) => {
    const socket = socketRef.current;
    if (!socket || socket.readyState !== WebSocket.OPEN || !conversationId) {
      setError('WebSocket 尚未连接，暂时无法发送消息。');
      return;
    }

    const trimmed = content.trim();
    if (!trimmed) {
      return;
    }

    setMessages((current) => {
      const existing = current.find((message) => message.id === messageId);
      if (existing) {
        return current.map((message) =>
          message.id === messageId ? { ...message, content: trimmed, status: 'pending' } : message
        );
      }

      return [
        ...current,
        {
          id: messageId,
          role: 'visitor',
          content: trimmed,
          createdAt: formatClock(),
          status: 'pending'
        }
      ];
    });

    setIsLoading(true);
    setError(null);
    socket.send(
      JSON.stringify({
        conversationId,
        clientMessageId: messageId,
        language: 'zh-CN',
        message: trimmed
      })
    );
  };

  return (
    <div
      style={{
        minHeight: '100vh',
        background: 'linear-gradient(180deg, #f2f5f7 0%, #ebeff3 100%)',
        padding: isPhone ? '18px 12px 28px' : isCompact ? '24px 16px 36px' : '32px 20px 44px',
        color: '#0f172a',
        fontFamily:
          '"IBM Plex Sans", "PingFang SC", "Hiragino Sans GB", "Noto Sans CJK SC", "Microsoft YaHei", sans-serif'
      }}
    >
      <div style={{ maxWidth: 980, margin: '0 auto' }} aria-label={subtitle ? `${title} ${subtitle}` : title}>
        <section
          style={{
            overflow: 'hidden',
            border: '1px solid rgba(148, 163, 184, 0.18)',
            background: 'rgba(255,255,255,0.92)',
            boxShadow: '0 24px 60px rgba(15, 23, 42, 0.08)'
          }}
        >
            <div
              ref={transcriptRef}
              style={{
                minHeight: isPhone ? 420 : 540,
                maxHeight: isPhone ? 'none' : '62vh',
                overflowY: 'auto',
                padding: isPhone ? 16 : 24,
                display: 'grid',
                gap: 18,
                background: 'linear-gradient(180deg, #fcfdff 0%, #f8fafc 100%)'
              }}
            >
              {isInitializing ? (
                <div
                  style={{
                    alignSelf: 'center',
                    justifySelf: 'center',
                    color: theme.mutedText,
                    fontSize: 14
                  }}
                >
                  正在连接会话...
                </div>
              ) : null}

              {messages.map((message) => {
                const isVisitor = message.role === 'visitor';

                return (
                  <article
                    key={message.id}
                    style={{
                      justifySelf: isVisitor ? 'end' : 'start',
                      maxWidth: isPhone ? '100%' : '80%',
                      display: 'flex',
                      gap: 12,
                      alignItems: 'flex-start',
                      flexDirection: isVisitor ? 'row-reverse' : 'row'
                    }}
                  >
                    {isVisitor ? <VisitorAvatar themeColor={themeColor} /> : <AssistantAvatar />}

                    <div style={{ display: 'grid', gap: 8, minWidth: 0 }}>
                      <div
                        style={{
                          display: 'flex',
                          gap: 8,
                          alignItems: 'center',
                          justifyContent: isVisitor ? 'flex-end' : 'flex-start',
                          color: theme.mutedText,
                          fontSize: 11,
                          flexWrap: 'wrap'
                        }}
                      >
                        <span
                          style={{
                            color: isVisitor ? theme.mutedText : '#64748b',
                            fontWeight: isVisitor ? 500 : 400,
                            letterSpacing: isVisitor ? 'normal' : '0.02em'
                          }}
                        >
                          {isVisitor ? '你' : sourceTypeLabel(message.sourceType)}
                        </span>
                        <span>{message.createdAt}</span>
                        {isVisitor && message.status === 'pending' ? <span>发送中</span> : null}
                        {isVisitor && message.status === 'failed' ? (
                          <button
                            type="button"
                            onClick={() => submitMessage(message.content, message.id)}
                            disabled={connectionStatus !== 'connected' || isLoading}
                            style={{
                              border: 'none',
                              background: 'transparent',
                              color: '#dc2626',
                              padding: 0,
                              cursor: 'pointer',
                              fontWeight: 700
                            }}
                          >
                            重试发送
                          </button>
                        ) : null}
                      </div>

                      <div
                        style={{
                          padding: '14px 16px',
                          borderRadius: isVisitor ? '10px 10px 2px 10px' : '10px 10px 10px 2px',
                          background: isVisitor
                            ? `linear-gradient(135deg, ${themeColor}, ${fade(themeColor, 0.86)})`
                            : '#ffffff',
                          color: isVisitor ? '#fff' : theme.pageForeground,
                          border: isVisitor ? 'none' : '1px solid rgba(148, 163, 184, 0.18)',
                          boxShadow: isVisitor
                            ? '0 12px 24px rgba(15, 23, 42, 0.12)'
                            : '0 10px 22px rgba(15, 23, 42, 0.06)'
                        }}
                      >
                        <div style={{ whiteSpace: 'pre-wrap', lineHeight: 1.7 }}>{message.content}</div>

                        {!isVisitor && message.citations && message.citations.length > 0 ? (
                          <div style={{ display: 'grid', gap: 10, marginTop: 14 }}>
                            <div style={{ fontSize: 11, color: '#94a3b8', letterSpacing: '0.02em' }}>引用来源</div>
                            <div style={{ display: 'grid', gap: 8 }}>
                              {message.citations.map((citation) => (
                                <a
                                  key={`${message.id}-${citation.sourceType}-${citation.sourceId}`}
                                  href={citation.sourceLink ?? undefined}
                                  target={citation.sourceLink ? '_blank' : undefined}
                                  rel={citation.sourceLink ? 'noreferrer' : undefined}
                                  style={{
                                    display: 'flex',
                                    alignItems: 'flex-start',
                                    justifyContent: 'space-between',
                                    gap: 12,
                                    background: '#f8fafc',
                                    color: theme.pageForeground,
                                    fontSize: 12,
                                    padding: '10px 12px',
                                    textDecoration: 'none',
                                    borderLeft: '2px solid rgba(148, 163, 184, 0.28)'
                                  }}
                                >
                                  <div style={{ display: 'grid', gap: 3, minWidth: 0 }}>
                                    <strong style={{ fontSize: 13 }}>
                                      {citation.sourceType === 'KNOWLEDGE' ? '知识库' : citation.sourceType}: {citation.title}
                                    </strong>
                                    <span style={{ color: theme.mutedText }}>
                                      {citation.sourceLink ? '查看来源文档' : '来源已记录在会话上下文中'}
                                    </span>
                                  </div>
                                  <span style={{ color: '#94a3b8', flexShrink: 0 }} aria-hidden="true">
                                    ›
                                  </span>
                                </a>
                              ))}
                            </div>
                          </div>
                        ) : null}
                      </div>
                    </div>
                  </article>
                );
              })}

              {isLoading ? (
                <div
                  style={{
                    justifySelf: 'start',
                    display: 'flex',
                    gap: 12,
                    alignItems: 'flex-start'
                  }}
                >
                  <AssistantAvatar />
                  <div
                    style={{
                      borderRadius: '10px 10px 10px 2px',
                      background: '#ffffff',
                      border: '1px solid rgba(148, 163, 184, 0.18)',
                      padding: '14px 16px',
                      boxShadow: '0 10px 22px rgba(15, 23, 42, 0.06)'
                    }}
                  >
                    <div style={{ display: 'flex', gap: 8, alignItems: 'center' }}>
                      <span style={{ color: '#64748b', fontSize: 11, letterSpacing: '0.02em' }}>系统</span>
                      <span style={{ color: theme.mutedText }}>正在生成回答...</span>
                    </div>
                  </div>
                </div>
              ) : null}
            </div>

            <div style={{ padding: isPhone ? 16 : 24, borderTop: '1px solid rgba(148, 163, 184, 0.14)' }}>
              {draft.trim() ? null : (
                <div style={{ marginBottom: 14 }}>
                  <div style={{ display: 'flex', gap: 8, flexWrap: 'wrap' }}>
                    {QUICK_PROMPTS.map((prompt) => (
                      <button
                        key={prompt}
                        type="button"
                        onClick={() => setDraft(prompt)}
                        style={{
                          borderRadius: 0,
                          border: '1px solid rgba(148, 163, 184, 0.14)',
                          background: 'rgba(248, 250, 252, 0.88)',
                          color: '#64748b',
                          padding: '6px 10px',
                          fontSize: 12,
                          cursor: 'pointer'
                        }}
                      >
                        {prompt}
                      </button>
                    ))}
                  </div>
                </div>
              )}

              <form
                onSubmit={(event) => {
                  event.preventDefault();
                  if (!canSend) {
                    return;
                  }

                  const nextDraft = draft.trim();
                  setDraft('');
                  submitMessage(nextDraft);
                }}
              >
                <div
                  style={{
                    borderRadius: 20,
                    background: '#ffffff',
                    border: '1px solid rgba(148, 163, 184, 0.20)',
                    padding: isPhone ? 14 : 16,
                    boxShadow: '0 6px 16px rgba(15, 23, 42, 0.04)'
                  }}
                >
                  <textarea
                    value={draft}
                    onChange={(event) => setDraft(event.target.value)}
                    onKeyDown={(event) => {
                      if (event.key === 'Enter' && !event.shiftKey) {
                        event.preventDefault();
                        if (canSend) {
                          const nextDraft = draft.trim();
                          setDraft('');
                          submitMessage(nextDraft);
                        }
                      }
                    }}
                    placeholder="输入访客问题。Enter 发送，Shift + Enter 换行"
                    rows={4}
                    style={{
                      width: '100%',
                      resize: 'none',
                      border: 'none',
                      outline: 'none',
                      fontSize: isPhone ? 14 : 15,
                      lineHeight: 1.75,
                      color: '#0f172a',
                      fontFamily: 'inherit',
                      background: 'transparent'
                    }}
                  />

                  <div
                    style={{
                      display: 'flex',
                      justifyContent: 'space-between',
                      alignItems: 'center',
                      gap: 12,
                      marginTop: 12,
                      flexWrap: 'wrap'
                    }}
                  >
                    <div
                      style={{
                        display: 'flex',
                        alignItems: 'center',
                        gap: 8,
                        background: 'rgba(248,250,252,0.92)',
                        border: '1px solid rgba(148, 163, 184, 0.16)',
                        padding: '6px 10px',
                        boxShadow: '0 4px 12px rgba(15, 23, 42, 0.03)'
                      }}
                    >
                      <span
                        aria-hidden="true"
                        style={{
                          width: 8,
                          height: 8,
                          borderRadius: '50%',
                          background: connectionAppearance.dot,
                          boxShadow: 'none',
                          flexShrink: 0
                        }}
                      />
                      <strong style={{ fontSize: 12, fontWeight: 600, color: connectionAppearance.color }}>
                        {connectionLabel(connectionStatus)}
                      </strong>
                    </div>

                    <button
                      type="submit"
                      disabled={!canSend}
                      style={{
                        borderRadius: 0,
                        border: 'none',
                        background: canSend ? '#0f172a' : '#cbd5e1',
                        color: '#fff',
                        padding: '12px 18px',
                        minWidth: isPhone ? '100%' : 132,
                        cursor: canSend ? 'pointer' : 'not-allowed',
                        fontWeight: 700
                      }}
                    >
                      {isLoading
                        ? '处理中...'
                        : connectionStatus !== 'connected'
                          ? connectionLabel(connectionStatus)
                          : '发送消息'}
                    </button>
                  </div>
                </div>
              </form>
            </div>
            {error ? (
              <div
                style={{
                  margin: '0 16px 16px',
                  borderRadius: 0,
                  background: '#fef2f2',
                  color: '#b91c1c',
                  padding: '12px 14px',
                  border: '1px solid rgba(239, 68, 68, 0.18)'
                }}
              >
                {error}
              </div>
            ) : null}
        </section>
      </div>
    </div>
  );
}
