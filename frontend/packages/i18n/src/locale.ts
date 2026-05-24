export type Locale = 'zh-CN' | 'en-US';

export const resources: Record<Locale, Record<string, string>> = {
  'zh-CN': {
    dashboard: '工作台',
    chatbots: 'Chatbot',
    tenants: '租户',
    plans: '套餐',
    conversations: '会话',
    login: '登录',
    chatbotWidget: '网站插件',
    chatPage: '独立聊天页'
  },
  'en-US': {
    dashboard: 'Dashboard',
    chatbots: 'Chatbots',
    tenants: 'Tenants',
    plans: 'Plans',
    conversations: 'Conversations',
    login: 'Login',
    chatbotWidget: 'Widget',
    chatPage: 'Chat Page'
  }
};
