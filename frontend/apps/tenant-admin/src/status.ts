import type { ChatbotSummary, ConversationDetail } from '@agentx/api-client';

export function statusColor(status: string) {
  if (status === 'ACTIVE') {
    return { color: '#166534', background: '#dcfce7', label: '启用中' };
  }

  if (status === 'DISABLED') {
    return { color: '#991b1b', background: '#fee2e2', label: '已停用' };
  }

  if (status === 'DELETED') {
    return { color: '#475569', background: '#e2e8f0', label: '已删除' };
  }

  return { color: '#92400e', background: '#fef3c7', label: '草稿' };
}

export function describeDirectModel(chatbot: Pick<ChatbotSummary, 'allowDirectModel' | 'providerCode' | 'modelCode'>) {
  if (!chatbot.allowDirectModel) {
    return '关闭';
  }

  if (chatbot.providerCode && chatbot.modelCode) {
    return `${chatbot.providerCode} / ${chatbot.modelCode}`;
  }

  return '系统默认模型';
}

export function describeEmbeddingModel(chatbot: Pick<ChatbotSummary, 'embeddingProviderCode' | 'embeddingModelCode'>) {
  if (chatbot.embeddingProviderCode && chatbot.embeddingModelCode) {
    return `${chatbot.embeddingProviderCode} / ${chatbot.embeddingModelCode}`;
  }

  return '系统默认 Embedding';
}

export function conversationStatusText(status: string) {
  if (status === 'ACTIVE') {
    return '进行中';
  }

  if (status === 'ENDED') {
    return '已结束';
  }

  if (status === 'HANDOFF_PENDING') {
    return '待转人工';
  }

  if (status === 'DELETED') {
    return '已删除';
  }

  return status;
}

export function knowledgeSourceStatusText(status: string) {
  if (status === 'ACTIVE') {
    return '启用中';
  }

  if (status === 'DISABLED') {
    return '已停用';
  }

  if (status === 'DELETED') {
    return '已删除';
  }

  if (status === 'FAILED') {
    return '处理失败';
  }

  if (status === 'PENDING') {
    return '待处理';
  }

  return status;
}

export function describeConversationModelSummary(conversation: ConversationDetail) {
  if (conversation.modelCalls.length === 0) {
    return {
      latest: '未命中模型调用',
      totalTokens: 0,
      totalCost: 0,
      successfulCalls: 0
    };
  }

  const latestCall = conversation.modelCalls[conversation.modelCalls.length - 1];
  return {
    latest: `${latestCall.provider} / ${latestCall.model}`,
    totalTokens: conversation.modelCalls.reduce((sum, call) => sum + call.totalTokens, 0),
    totalCost: conversation.modelCalls.reduce((sum, call) => sum + call.estimatedCost, 0),
    successfulCalls: conversation.modelCalls.filter((call) => call.status === 'SUCCESS').length
  };
}
