import { useEffect, useState } from 'react';

import { Select } from 'antd';
import { ActionButton, DetailModal, FilterSection, ListSection, ListTable, ModalActionBar, NoticeBanner, PageStack, RowActionBar, RowActionButton, SectionHeader, StatusTag, SurfaceCard, TableLinkButton } from '@agentx/admin-ui';
import {
  deleteConversation,
  exportConversation,
  getConversation,
  listChatbots,
  listConversations,
  type AuthSession,
  type ChatbotSummary,
  type ConversationDetail,
  type ConversationSummary,
  updateConversationStatus
} from '@agentx/api-client';

import { Field, inputStyle } from '../form-ui';
import { conversationStatusText, describeConversationModelSummary, statusColor } from '../status';

export function ConversationsPage({ session }: { session: AuthSession }) {
  const token = session.accessToken;
  const [chatbots, setChatbots] = useState<ChatbotSummary[]>([]);
  const [chatbotId, setChatbotId] = useState<number | null>(null);
  const [status, setStatus] = useState<ConversationSummary['status'] | ''>('');
  const [detailOpen, setDetailOpen] = useState(false);
  const [conversations, setConversations] = useState<ConversationSummary[]>([]);
  const [selectedConversationId, setSelectedConversationId] = useState<number | null>(null);
  const [conversationDetail, setConversationDetail] = useState<ConversationDetail | null>(null);
  const [loading, setLoading] = useState(true);
  const [detailLoading, setDetailLoading] = useState(false);
  const [submitting, setSubmitting] = useState(false);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);
  const [notice, setNotice] = useState<string | null>(null);
  const conversationModelSummary = conversationDetail ? describeConversationModelSummary(conversationDetail) : null;

  useEffect(() => {
    if (!session.tenantId) {
      return;
    }

    void listChatbots(token, session.tenantId)
      .then((nextChatbots) => setChatbots(nextChatbots))
      .catch(() => undefined);
  }, [session.tenantId, token]);

  useEffect(() => {
    setLoading(true);
    setErrorMessage(null);

    void listConversations(token, {
      chatbotId: chatbotId ?? undefined,
      status: status || undefined
    })
      .then((nextConversations) => {
        setConversations(nextConversations);
        setSelectedConversationId((current) => (current && nextConversations.some((conversation) => conversation.id === current) ? current : null));
      })
      .catch((error) => setErrorMessage(error instanceof Error ? error.message : '会话列表加载失败。'))
      .finally(() => setLoading(false));
  }, [chatbotId, status, token]);

  useEffect(() => {
    if (!selectedConversationId) {
      setConversationDetail(null);
      return;
    }

    setDetailLoading(true);

    void getConversation(token, selectedConversationId)
      .then((detail) => setConversationDetail(detail))
      .catch((error) => setErrorMessage(error instanceof Error ? error.message : '会话详情加载失败。'))
      .finally(() => setDetailLoading(false));
  }, [selectedConversationId, token]);

  const handleStatusChange = async (nextStatus: ConversationSummary['status'], conversationIdOverride?: number | null) => {
    const conversationId = conversationIdOverride ?? selectedConversationId;

    if (!conversationId) {
      return;
    }

    setSubmitting(true);
    setErrorMessage(null);
    setNotice(null);

    try {
      const updatedConversation = await updateConversationStatus(token, conversationId, nextStatus);
      setConversations((current) =>
        current.map((conversation) =>
          conversation.id === updatedConversation.id ? { ...conversation, status: updatedConversation.status } : conversation
        )
      );
      setConversationDetail((current) => (current ? { ...current, status: updatedConversation.status } : current));
      setNotice(`会话状态已更新为 ${updatedConversation.status}。`);
    } catch (error) {
      setErrorMessage(error instanceof Error ? error.message : '会话状态更新失败。');
    } finally {
      setSubmitting(false);
    }
  };

  const handleExport = async (conversationIdOverride?: number | null) => {
    const conversationId = conversationIdOverride ?? selectedConversationId;

    if (!conversationId) {
      return;
    }

    setSubmitting(true);
    setErrorMessage(null);
    setNotice(null);

    try {
      const exportResult = await exportConversation(token, conversationId);
      const objectUrl = window.URL.createObjectURL(exportResult.blob);
      const anchor = document.createElement('a');
      anchor.href = objectUrl;
      anchor.download = exportResult.fileName ?? `conversation-${conversationId}.json`;
      anchor.click();
      window.URL.revokeObjectURL(objectUrl);
      setNotice(`会话 ${conversationId} 已导出为 ${anchor.download}。`);
    } catch (error) {
      setErrorMessage(error instanceof Error ? error.message : '会话导出失败。');
    } finally {
      setSubmitting(false);
    }
  };

  const handleDelete = async (conversationIdOverride?: number | null) => {
    const conversationId = conversationIdOverride ?? selectedConversationId;

    if (!conversationId) {
      return;
    }

    const confirmed = window.confirm('删除后该会话将被标记为 DELETED，并写入审计日志。是否继续？');

    if (!confirmed) {
      return;
    }

    setSubmitting(true);
    setErrorMessage(null);
    setNotice(null);

    try {
      const deletedConversation = await deleteConversation(token, conversationId);
      setConversations((current) =>
        current.map((conversation) =>
          conversation.id === deletedConversation.id ? { ...conversation, status: deletedConversation.status } : conversation
        )
      );
      setConversationDetail((current) => (current ? { ...current, status: deletedConversation.status } : current));
      setNotice(`会话 ${conversationId} 已标记为 DELETED。`);
    } catch (error) {
      setErrorMessage(error instanceof Error ? error.message : '会话删除失败。');
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <>
      <SectionHeader title="会话管理" />

      <PageStack>
        {errorMessage ? <NoticeBanner tone="error">{errorMessage}</NoticeBanner> : null}
        {notice ? <NoticeBanner tone="notice">{notice}</NoticeBanner> : null}
      </PageStack>

      <PageStack>
        <FilterSection title="筛选条件" description="会话筛选和列表操作都保留在同一页。">
          <Field label="机器人">
            <Select
              value={chatbotId == null ? '' : String(chatbotId)}
              onChange={(value) => setChatbotId(value ? Number(value) : null)}
              style={{ width: '100%' }}
              options={[{ value: '', label: '全部机器人' }, ...chatbots.map((chatbot) => ({ value: String(chatbot.id), label: chatbot.name }))]}
            />
          </Field>
          <Field label="状态">
            <Select
              value={status}
              onChange={(value) => setStatus(value as ConversationSummary['status'] | '')}
              style={{ width: '100%' }}
              options={[
                { value: '', label: '全部状态' },
                { value: 'ACTIVE', label: conversationStatusText('ACTIVE') },
                { value: 'ENDED', label: conversationStatusText('ENDED') },
                { value: 'HANDOFF_PENDING', label: conversationStatusText('HANDOFF_PENDING') },
                { value: 'DELETED', label: conversationStatusText('DELETED') }
              ]}
            />
          </Field>
        </FilterSection>

        <ListSection title="会话列表" description="点击会话名称查看详情，末列直接处理状态流转、导出和删除。">
          <ListTable
            rowKey="id"
            dataSource={conversations}
            loading={loading}
            emptyText="当前没有符合条件的会话。"
            columns={[
              {
                key: 'chatbotName',
                title: '所属机器人',
                render: (conversation) => (
                  <TableLinkButton
                    onClick={() => {
                      setSelectedConversationId(conversation.id);
                      setDetailOpen(true);
                    }}
                  >
                    {conversation.chatbotName}
                  </TableLinkButton>
                )
              },
              { key: 'visitor', title: '访客', render: (conversation) => conversation.anonymousVisitorId },
              { key: 'entryType', title: '入口', render: (conversation) => conversation.entryType },
              { key: 'messageCount', title: '消息数', render: (conversation) => conversation.messageCount },
              { key: 'latestMessage', title: '最新消息', render: (conversation) => conversation.latestMessage || '暂无消息内容' },
              {
                key: 'status',
                title: '状态',
                render: (conversation) => {
                  const badge = statusColor(conversation.status);
                  return <StatusTag color={conversation.status === 'ACTIVE' ? 'success' : badge.color === '#991b1b' ? 'error' : 'default'}>{badge.label}</StatusTag>;
                }
              },
              {
                key: 'actions',
                title: '操作',
                width: 300,
                render: (conversation) => (
                  <RowActionBar>
                    <RowActionButton onClick={() => { setSelectedConversationId(conversation.id); setDetailOpen(true); }}>查看</RowActionButton>
                    <RowActionButton onClick={() => { setSelectedConversationId(conversation.id); void handleStatusChange('ACTIVE', conversation.id); }} disabled={submitting}>启用</RowActionButton>
                    <RowActionButton onClick={() => { setSelectedConversationId(conversation.id); void handleStatusChange('ENDED', conversation.id); }} disabled={submitting}>结束</RowActionButton>
                    <RowActionButton onClick={() => { setSelectedConversationId(conversation.id); void handleExport(conversation.id); }} disabled={submitting}>导出</RowActionButton>
                    <RowActionButton onClick={() => { setSelectedConversationId(conversation.id); void handleDelete(conversation.id); }} disabled={submitting || conversation.status === 'DELETED'} danger>删除</RowActionButton>
                  </RowActionBar>
                )
              }
            ]}
          />
        </ListSection>
      </PageStack>

      <DetailModal open={detailOpen && !!selectedConversationId} title={selectedConversationId ? `会话 #${selectedConversationId}` : '会话详情'} onCancel={() => setDetailOpen(false)} width={1080}>
        {detailLoading ? (
          <div style={{ color: '#64748b' }}>详情加载中...</div>
        ) : !conversationDetail ? (
          <div style={{ color: '#64748b' }}>从会话列表中选择一条会话查看详情。</div>
        ) : (
          <div style={{ display: 'grid', gap: 16 }}>
            <SurfaceCard title="会话详情" description="查看来源元数据和完整消息序列。">
              <div style={{ display: 'grid', gap: 8, color: '#334155' }}>
                <div>机器人：{conversationDetail.chatbotName}</div>
                <div>访客标识：{conversationDetail.anonymousVisitorId}</div>
                <div>入口：{conversationDetail.entryType}</div>
                <div>状态：{conversationDetail.status}</div>
                <div>消息数：{conversationDetail.messages.length}</div>
                <div>域名：{conversationDetail.metadata.domain ?? '-'}</div>
                <div>IP：{conversationDetail.metadata.ipAddress ?? '-'}</div>
                <div>User Agent：{conversationDetail.metadata.userAgent ?? '-'}</div>
              </div>
              {conversationModelSummary && (
                <div
                  style={{
                    display: 'grid',
                    gap: 8,
                    padding: 14,
                    borderRadius: 16,
                    background: '#fff7ed',
                    border: '1px solid #fed7aa',
                    color: '#7c2d12'
                  }}
                >
                  <strong>模型概览</strong>
                  <div>最近一次命中：{conversationModelSummary.latest}</div>
                  <div>成功调用：{conversationModelSummary.successfulCalls} / {conversationDetail.modelCalls.length}</div>
                  <div>累计 Token：{conversationModelSummary.totalTokens}</div>
                  <div>累计估算成本：{conversationModelSummary.totalCost.toFixed(4)}</div>
                </div>
              )}
              <ModalActionBar>
                <ActionButton disabled={submitting} onClick={() => void handleStatusChange('ACTIVE')} tone="success">标记为进行中</ActionButton>
                <ActionButton disabled={submitting} onClick={() => void handleStatusChange('ENDED')} tone="warning">标记为已结束</ActionButton>
                <ActionButton disabled={submitting} onClick={() => void handleStatusChange('HANDOFF_PENDING')} tone="primary">标记为待转人工</ActionButton>
                <ActionButton disabled={submitting} onClick={() => void handleExport()} tone="neutral">导出 JSON</ActionButton>
                <ActionButton disabled={submitting || conversationDetail.status === 'DELETED'} onClick={() => void handleDelete()} tone="danger">删除会话</ActionButton>
              </ModalActionBar>
            </SurfaceCard>

            <SurfaceCard title="消息序列" description="完整展示用户和助手消息、引用以及模型元数据。">
              <div style={{ display: 'grid', gap: 12 }}>
                {conversationDetail.messages.map((message) => (
                  <div key={message.id} style={{ borderRadius: 16, padding: 14, background: message.role === 'ASSISTANT' ? '#fff7ed' : '#f8fafc', border: '1px solid #e2e8f0' }}>
                    <div style={{ display: 'flex', justifyContent: 'space-between', gap: 12, marginBottom: 8 }}>
                      <strong>{message.role}</strong>
                      <span style={{ color: '#64748b', fontSize: 12 }}>{message.createdAt}</span>
                    </div>
                    <div style={{ color: '#334155', whiteSpace: 'pre-wrap' }}>{message.content}</div>
                    {(message.sourceType || message.citations.length > 0 || message.model) && (
                      <div style={{ marginTop: 10, display: 'grid', gap: 6, padding: 10, borderRadius: 12, background: '#ffffff', border: '1px dashed #cbd5e1', color: '#475569', fontSize: 13 }}>
                        <div>来源类型：{message.sourceType ?? '-'}</div>
                        <div>语言：{message.language ?? '-'}</div>
                        {message.knowledgeScore !== null && <div>知识命中分数：{message.knowledgeScore}</div>}
                        {message.citations.length > 0 && (
                          <div>
                            引用来源：{message.citations.map((citation) => citation.title ?? citation.sourceType ?? '未命名来源').join(' / ')}
                          </div>
                        )}
                        {message.model && (
                          <>
                            <div>模型：{message.model.provider ?? '-'} / {message.model.model ?? '-'}</div>
                            <div>模式：{message.model.mode ?? '-'}</div>
                            <div>Token：{message.model.totalTokens ?? 0}（Prompt {message.model.promptTokens ?? 0} / Completion {message.model.completionTokens ?? 0}）</div>
                            <div>估算成本：{message.model.estimatedCost?.toFixed(4) ?? '0.0000'}</div>
                          </>
                        )}
                      </div>
                    )}
                  </div>
                ))}
              </div>
            </SurfaceCard>

            {conversationDetail.modelCalls.length > 0 && (
              <SurfaceCard title="模型调用日志" description="展示当前会话内的模型调用记录。">
                <div style={{ display: 'grid', gap: 12 }}>
                  {conversationDetail.modelCalls.map((call) => (
                    <div key={call.id} style={{ borderRadius: 14, border: '1px solid #cbd5e1', background: '#f8fafc', padding: 12, color: '#334155' }}>
                      <div style={{ display: 'flex', justifyContent: 'space-between', gap: 12, marginBottom: 6 }}>
                        <strong>{call.provider} / {call.model}</strong>
                        <span style={{ color: '#64748b', fontSize: 12 }}>{call.createdAt}</span>
                      </div>
                      <div>用途：{call.purpose}，状态：{call.status}，耗时：{call.latencyMs}ms</div>
                      <div>Token：{call.totalTokens}（Prompt {call.promptTokens} / Completion {call.completionTokens}）</div>
                      <div>估算成本：{call.estimatedCost.toFixed(4)}</div>
                    </div>
                  ))}
                </div>
              </SurfaceCard>
            )}
          </div>
        )}
      </DetailModal>
    </>
  );
}
