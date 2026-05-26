import type { FormEvent } from 'react';
import { useEffect, useState } from 'react';

import { Input, Select, Upload, type UploadFile } from 'antd';
import { ActionButton, ActionToolbar, DetailModal, FilterSection, ListSection, ListTable, ModalActionBar, NoticeBanner, PageStack, RowActionBar, RowActionButton, SectionActionHeader, SectionHeader, StatusTag, SurfaceCard, TableLinkButton, WorkspaceTabs } from '@agentx/admin-ui';
import {
  ApiRequestError,
  createFaq,
  createWebKnowledgeSource,
  deleteKnowledgeSource,
  exportFaqs,
  getKnowledgeSource,
  importFaqs,
  listChatbots,
  listFaqs,
  listKnowledgeSources,
  refreshKnowledgeSource,
  retryKnowledgeSource,
  type AuthSession,
  type ChatbotSummary,
  type CreateWebKnowledgeSourceRequest,
  type FaqSummary,
  type ImportFaqResult,
  type KnowledgeSourceDetail,
  type KnowledgeSourceSummary,
  type UpdateFaqRequest,
  updateFaq,
  updateFaqStatus,
  updateFaqStatuses,
  updateKnowledgeSourceStatus,
  uploadKnowledgeFile
} from '@agentx/api-client';

import { emptyFaqForm, Field, FormCard, inputStyle, normalizeLines, type FaqFormState } from '../form-ui';
import { knowledgeSourceStatusText, statusColor } from '../status';

const { TextArea } = Input;

export function KnowledgePage({ session }: { session: AuthSession }) {
  const tenantId = session.tenantId ?? 0;
  const token = session.accessToken;
  const [chatbots, setChatbots] = useState<ChatbotSummary[]>([]);
  const [selectedChatbotId, setSelectedChatbotId] = useState<number | null>(null);
  const [workspaceTab, setWorkspaceTab] = useState('faq');
  const [knowledgeDetailOpen, setKnowledgeDetailOpen] = useState(false);
  const [faqDetailOpen, setFaqDetailOpen] = useState(false);
  const [languageFilter, setLanguageFilter] = useState('');
  const [keywordFilter, setKeywordFilter] = useState('');
  const [statusFilter, setStatusFilter] = useState<FaqSummary['status'] | ''>('');
  const [knowledgeSources, setKnowledgeSources] = useState<KnowledgeSourceSummary[]>([]);
  const [selectedKnowledgeSourceId, setSelectedKnowledgeSourceId] = useState<number | null>(null);
  const [selectedKnowledgeSource, setSelectedKnowledgeSource] = useState<KnowledgeSourceDetail | null>(null);
  const [selectedKnowledgeFile, setSelectedKnowledgeFile] = useState<File | null>(null);
  const [webSourceForm, setWebSourceForm] = useState<CreateWebKnowledgeSourceRequest>({
    name: '',
    url: ''
  });
  const [faqs, setFaqs] = useState<FaqSummary[]>([]);
  const [selectedFaqId, setSelectedFaqId] = useState<number | null>(null);
  const [selectedFaqIds, setSelectedFaqIds] = useState<number[]>([]);
  const [loading, setLoading] = useState(true);
  const [submitting, setSubmitting] = useState(false);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);
  const [notice, setNotice] = useState<string | null>(null);
  const [createOpen, setCreateOpen] = useState(false);
  const [editOpen, setEditOpen] = useState(false);
  const [importOpen, setImportOpen] = useState(false);
  const [importPayload, setImportPayload] = useState('');
  const [importResult, setImportResult] = useState<ImportFaqResult | null>(null);
  const [createForm, setCreateForm] = useState<FaqFormState>(emptyFaqForm(tenantId, 0));
  const [editForm, setEditForm] = useState<UpdateFaqRequest>({
    language: 'zh-CN',
    question: '',
    alternateQuestions: [],
    answer: ''
  });

  const selectedFaq = faqs.find((faq) => faq.id === selectedFaqId) ?? null;
  const selectedKnowledgeUploadList: UploadFile[] = selectedKnowledgeFile
    ? [{ uid: 'selected-knowledge-file', name: selectedKnowledgeFile.name, size: selectedKnowledgeFile.size, status: 'done' }]
    : [];

  const loadFaqs = async (chatbotId: number, preserveSelection = true) => {
    const nextFaqs = await listFaqs(token, {
      tenantId,
      chatbotId,
      language: languageFilter || undefined,
      keyword: keywordFilter || undefined,
      status: statusFilter || undefined
    });

    setFaqs(nextFaqs);
    setSelectedFaqId((current) => {
      if (preserveSelection && current && nextFaqs.some((faq) => faq.id === current)) {
        return current;
      }

      return null;
    });
    setSelectedFaqIds((current) => current.filter((faqId) => nextFaqs.some((faq) => faq.id === faqId)));
    setCreateForm(emptyFaqForm(tenantId, chatbotId));
  };

  const loadKnowledgeSources = async (chatbotId: number) => {
    const nextSources = await listKnowledgeSources(token, tenantId, chatbotId);
    setKnowledgeSources(nextSources);
    setSelectedKnowledgeSourceId((current) => (current && nextSources.some((source) => source.id === current) ? current : null));
  };

  const loadKnowledgeSourceDetail = async (chatbotId: number, sourceId: number) => {
    const nextSource = await getKnowledgeSource(token, tenantId, chatbotId, sourceId);
    setSelectedKnowledgeSource(nextSource);
  };

  useEffect(() => {
    if (!tenantId) {
      return;
    }

    void listChatbots(token, tenantId)
      .then((nextChatbots) => {
        setChatbots(nextChatbots);
        setSelectedChatbotId(nextChatbots[0]?.id ?? null);
      })
      .catch((error) => setErrorMessage(error instanceof Error ? error.message : 'Chatbot 列表加载失败。'));
  }, [tenantId, token]);

  useEffect(() => {
    if (!selectedChatbotId) {
      setFaqs([]);
      setKnowledgeSources([]);
      setSelectedKnowledgeSourceId(null);
      setSelectedKnowledgeSource(null);
      setSelectedFaqId(null);
      setSelectedFaqIds([]);
      setLoading(false);
      return;
    }

    setLoading(true);
    setErrorMessage(null);

    void Promise.all([loadFaqs(selectedChatbotId), loadKnowledgeSources(selectedChatbotId)])
      .catch((error) => setErrorMessage(error instanceof Error ? error.message : 'FAQ 列表加载失败。'))
      .finally(() => setLoading(false));
  }, [selectedChatbotId, tenantId, token, languageFilter, keywordFilter, statusFilter]);

  useEffect(() => {
    if (!selectedChatbotId || !selectedKnowledgeSourceId) {
      setSelectedKnowledgeSource(null);
      return;
    }

    void loadKnowledgeSourceDetail(selectedChatbotId, selectedKnowledgeSourceId).catch((error) =>
      setErrorMessage(error instanceof Error ? error.message : '知识来源详情加载失败。')
    );
  }, [selectedChatbotId, selectedKnowledgeSourceId, tenantId, token]);

  useEffect(() => {
    if (!selectedFaq) {
      return;
    }

    setEditForm({
      language: selectedFaq.language,
      question: selectedFaq.question,
      alternateQuestions: selectedFaq.alternateQuestions,
      answer: selectedFaq.answer
    });
  }, [selectedFaq]);

  const handleCreate = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    if (!selectedChatbotId) {
      return;
    }

    setSubmitting(true);
    setErrorMessage(null);
    setNotice(null);
    setImportResult(null);

    try {
      const createdFaq = await createFaq(token, {
        ...createForm,
        chatbotId: selectedChatbotId,
        language: createForm.language.trim(),
        question: createForm.question.trim(),
        alternateQuestions: createForm.alternateQuestions,
        answer: createForm.answer.trim()
      });
      await loadFaqs(selectedChatbotId, false);
      setSelectedFaqId(createdFaq.id);
      setCreateOpen(false);
      setNotice('FAQ 已创建。');
    } catch (error) {
      setErrorMessage(error instanceof Error ? error.message : 'FAQ 创建失败。');
    } finally {
      setSubmitting(false);
    }
  };

  const handleEdit = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    if (!selectedFaq) {
      return;
    }

    setSubmitting(true);
    setErrorMessage(null);
    setNotice(null);
    setImportResult(null);

    try {
      const updatedFaq = await updateFaq(token, selectedFaq.id, {
        language: editForm.language.trim(),
        question: editForm.question.trim(),
        alternateQuestions: editForm.alternateQuestions,
        answer: editForm.answer.trim()
      });
      setFaqs((current) => current.map((faq) => (faq.id === updatedFaq.id ? updatedFaq : faq)));
      setSelectedFaqIds((current) => (current.includes(updatedFaq.id) ? current : current));
      setEditOpen(false);
      setNotice('FAQ 已更新。');
    } catch (error) {
      setErrorMessage(error instanceof Error ? error.message : 'FAQ 更新失败。');
    } finally {
      setSubmitting(false);
    }
  };

  const handleStatusChange = async (status: FaqSummary['status'], faqOverride?: FaqSummary | null) => {
    const faq = faqOverride ?? selectedFaq;

    if (!faq) {
      return;
    }

    setSubmitting(true);
    setErrorMessage(null);
    setNotice(null);
    setImportResult(null);

    try {
      const updatedFaq = await updateFaqStatus(token, faq.id, status);
      setFaqs((current) => current.map((faq) => (faq.id === updatedFaq.id ? updatedFaq : faq)));
      setNotice(`FAQ 状态已更新为 ${statusColor(updatedFaq.status).label}。`);
    } catch (error) {
      setErrorMessage(error instanceof Error ? error.message : 'FAQ 状态更新失败。');
    } finally {
      setSubmitting(false);
    }
  };

  const handleBulkStatusChange = async (status: FaqSummary['status']) => {
    if (selectedFaqIds.length === 0) {
      return;
    }

    setSubmitting(true);
    setErrorMessage(null);
    setNotice(null);

    try {
      const updatedFaqs = await updateFaqStatuses(token, selectedFaqIds, status);
      const updatedMap = new Map(updatedFaqs.map((faq) => [faq.id, faq]));
      setFaqs((current) => current.map((faq) => updatedMap.get(faq.id) ?? faq));
      setNotice(`已批量更新 ${updatedFaqs.length} 条 FAQ 状态为 ${statusColor(status).label}。`);
    } catch (error) {
      setErrorMessage(error instanceof Error ? error.message : 'FAQ 批量状态更新失败。');
    } finally {
      setSubmitting(false);
    }
  };

  const handleExport = async () => {
    if (!selectedChatbotId) {
      return;
    }

    setSubmitting(true);
    setErrorMessage(null);
    setNotice(null);

    try {
      const exportBlob = await exportFaqs(token, tenantId, selectedChatbotId);
      const objectUrl = window.URL.createObjectURL(exportBlob);
      const anchor = document.createElement('a');
      anchor.href = objectUrl;
      anchor.download = `faq-${tenantId}-${selectedChatbotId}.json`;
      anchor.click();
      window.URL.revokeObjectURL(objectUrl);
      setNotice(`FAQ 已导出，Chatbot ID ${selectedChatbotId}。`);
    } catch (error) {
      setErrorMessage(error instanceof Error ? error.message : 'FAQ 导出失败。');
    } finally {
      setSubmitting(false);
    }
  };

  const handleImport = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    if (!selectedChatbotId) {
      return;
    }

    setSubmitting(true);
    setErrorMessage(null);
    setNotice(null);
    setImportResult(null);

    try {
      const parsed = JSON.parse(importPayload) as { items?: Array<{ language?: string; status?: FaqSummary['status']; question?: string; alternateQuestions?: string[]; answer?: string }> } | Array<{ language?: string; status?: FaqSummary['status']; question?: string; alternateQuestions?: string[]; answer?: string }>;
      const items = Array.isArray(parsed) ? parsed : parsed.items ?? [];
      const result = await importFaqs(
        token,
        tenantId,
        selectedChatbotId,
        items.map((item) => ({
          language: item.language ?? 'zh-CN',
          status: item.status,
          question: item.question ?? '',
          alternateQuestions: item.alternateQuestions ?? [],
          answer: item.answer ?? ''
        }))
      );
      setImportResult(result);
      await loadFaqs(selectedChatbotId, false);
      setNotice(`FAQ 导入完成，成功 ${result.importedCount} 条。`);
    } catch (error) {
      setErrorMessage(error instanceof Error ? error.message : 'FAQ 导入失败。');
    } finally {
      setSubmitting(false);
    }
  };

  const toggleFaqSelection = (faqId: number, checked: boolean) => {
    setSelectedFaqIds((current) => {
      if (checked) {
        return current.includes(faqId) ? current : [...current, faqId];
      }

      return current.filter((item) => item !== faqId);
    });
  };

  const handleKnowledgeUpload = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    if (!selectedChatbotId || !selectedKnowledgeFile) {
      return;
    }

    setSubmitting(true);
    setErrorMessage(null);
    setNotice(null);

    try {
      const uploadedSource = await uploadKnowledgeFile(token, tenantId, selectedChatbotId, selectedKnowledgeFile);
      await loadKnowledgeSources(selectedChatbotId);
      setSelectedKnowledgeFile(null);
      setSelectedKnowledgeSourceId(uploadedSource.id);
      setNotice(`知识文件 ${uploadedSource.sourceName} 已上传。`);
    } catch (error) {
      const message =
        error instanceof ApiRequestError && error.code === 'FILES_LIMIT_REACHED'
          ? '当前套餐的文件数量已达上限，请先清理或扩容。'
          : error instanceof ApiRequestError && error.code === 'STORAGE_MB_LIMIT_REACHED'
            ? '当前套餐的知识库存储额度已达上限，请先清理或扩容。'
            : error instanceof ApiRequestError && error.code === 'FILE_TYPE_NOT_SUPPORTED'
              ? '文件类型不支持，目前仅支持 txt、md、pdf、docx、csv、json。'
              : error instanceof ApiRequestError && error.code === 'FILE_SIZE_LIMIT_EXCEEDED'
                ? '文件大小超出限制，单个文件最大支持 10 MB。'
                : error instanceof Error
                  ? error.message
                  : '知识文件上传失败。';
      setErrorMessage(message);
    } finally {
      setSubmitting(false);
    }
  };

  const handleWebSourceCreate = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    if (!selectedChatbotId) {
      return;
    }

    setSubmitting(true);
    setErrorMessage(null);
    setNotice(null);

    try {
      const createdSource = await createWebKnowledgeSource(token, tenantId, selectedChatbotId, {
        name: webSourceForm.name.trim(),
        url: webSourceForm.url.trim()
      });
      await loadKnowledgeSources(selectedChatbotId);
      setWebSourceForm({ name: '', url: '' });
      setSelectedKnowledgeSourceId(createdSource.id);
      setNotice(`网页知识源 ${createdSource.sourceName} 已创建。`);
    } catch (error) {
      const message =
        error instanceof ApiRequestError && error.code === 'INVALID_SOURCE_URL'
          ? '网页地址无效，目前仅支持 http 或 https 链接。'
          : error instanceof ApiRequestError && error.code === 'URL_REQUIRED'
            ? '请输入网页地址。'
            : error instanceof Error
              ? error.message
              : '网页知识源创建失败。';
      setErrorMessage(message);
    } finally {
      setSubmitting(false);
    }
  };

  const handleKnowledgeRefresh = async (sourceIdOverride?: number | null) => {
    const sourceId = sourceIdOverride ?? selectedKnowledgeSourceId;

    if (!selectedChatbotId || !sourceId) {
      return;
    }

    setSubmitting(true);
    setErrorMessage(null);
    setNotice(null);

    try {
      const refreshedSource = await refreshKnowledgeSource(token, tenantId, selectedChatbotId, sourceId);
      await loadKnowledgeSources(selectedChatbotId);
      setSelectedKnowledgeSource(refreshedSource);
      setNotice(`知识来源 ${refreshedSource.sourceName} 已刷新。`);
    } catch (error) {
      setErrorMessage(error instanceof Error ? error.message : '知识来源刷新失败。');
    } finally {
      setSubmitting(false);
    }
  };

  const handleKnowledgeRetry = async (sourceIdOverride?: number | null) => {
    const sourceId = sourceIdOverride ?? selectedKnowledgeSourceId;

    if (!selectedChatbotId || !sourceId) {
      return;
    }

    setSubmitting(true);
    setErrorMessage(null);
    setNotice(null);

    try {
      const retriedSource = await retryKnowledgeSource(token, tenantId, selectedChatbotId, sourceId);
      await loadKnowledgeSources(selectedChatbotId);
      setSelectedKnowledgeSource(retriedSource);
      setNotice(`知识来源 ${retriedSource.sourceName} 已重试。`);
    } catch (error) {
      setErrorMessage(error instanceof Error ? error.message : '知识来源重试失败。');
    } finally {
      setSubmitting(false);
    }
  };

  const handleKnowledgeDisable = async (sourceIdOverride?: number | null) => {
    const sourceId = sourceIdOverride ?? selectedKnowledgeSourceId;

    if (!selectedChatbotId || !sourceId) {
      return;
    }

    setSubmitting(true);
    setErrorMessage(null);
    setNotice(null);

    try {
      const updatedSource = await updateKnowledgeSourceStatus(token, tenantId, selectedChatbotId, sourceId, 'DISABLED');
      await loadKnowledgeSources(selectedChatbotId);
      setSelectedKnowledgeSource(updatedSource);
      setNotice(`知识来源 ${updatedSource.sourceName} 已停用。`);
    } catch (error) {
      setErrorMessage(error instanceof Error ? error.message : '知识来源停用失败。');
    } finally {
      setSubmitting(false);
    }
  };

  const handleKnowledgeEnable = async (sourceIdOverride?: number | null) => {
    const sourceId = sourceIdOverride ?? selectedKnowledgeSourceId;

    if (!selectedChatbotId || !sourceId) {
      return;
    }

    setSubmitting(true);
    setErrorMessage(null);
    setNotice(null);

    try {
      const updatedSource = await updateKnowledgeSourceStatus(token, tenantId, selectedChatbotId, sourceId, 'ACTIVE');
      await loadKnowledgeSources(selectedChatbotId);
      setSelectedKnowledgeSource(updatedSource);
      setNotice(`知识来源 ${updatedSource.sourceName} 已启用。`);
    } catch (error) {
      setErrorMessage(error instanceof Error ? error.message : '知识来源启用失败。');
    } finally {
      setSubmitting(false);
    }
  };

  const handleKnowledgeDelete = async (sourceIdOverride?: number | null) => {
    const sourceId = sourceIdOverride ?? selectedKnowledgeSourceId;

    if (!selectedChatbotId || !sourceId) {
      return;
    }

    const confirmed = window.confirm('删除后该知识来源将被标记为 DELETED，并释放已生成的 chunk。是否继续？');
    if (!confirmed) {
      return;
    }

    setSubmitting(true);
    setErrorMessage(null);
    setNotice(null);

    try {
      const deletedSource = await deleteKnowledgeSource(token, tenantId, selectedChatbotId, sourceId);
      await loadKnowledgeSources(selectedChatbotId);
      setNotice(`知识来源 ${deletedSource.sourceName} 已删除。`);
    } catch (error) {
      setErrorMessage(error instanceof Error ? error.message : '知识来源删除失败。');
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <>
      <SectionHeader
        title="FAQ 管理"
        actions={
          <div style={{ display: 'flex', gap: 12, flexWrap: 'wrap' }}>
            <ActionButton onClick={() => setImportOpen(true)} disabled={!selectedChatbotId} variant="outline" tone="neutral">
              导入 FAQ
            </ActionButton>
            <ActionButton onClick={() => void handleExport()} disabled={!selectedChatbotId || submitting} variant="outline" tone="neutral">
              导出 FAQ
            </ActionButton>
            <ActionButton onClick={() => setCreateOpen(true)} disabled={!selectedChatbotId} tone="warning">
              新建 FAQ
            </ActionButton>
          </div>
        }
      />

      <PageStack>
        {errorMessage ? <NoticeBanner tone="error">{errorMessage}</NoticeBanner> : null}
        {notice ? <NoticeBanner tone="notice">{notice}</NoticeBanner> : null}
      </PageStack>

      <WorkspaceTabs
        activeKey={workspaceTab}
        onChange={setWorkspaceTab}
        items={[
          {
            key: 'faq',
            label: 'FAQ 列表',
            children: (
              <div style={{ display: 'grid', gap: 16 }}>
                <FilterSection
                  title="FAQ 查询与批量操作"
                  description="查询条件和批量状态按钮集中在同一个列表工作区。"
                  columns="repeat(4, minmax(0, 1fr))"
                  actions={
                    <SectionActionHeader
                      description={selectedFaqIds.length === 0 ? '请选择 FAQ 后再执行批量操作。' : `当前已选择 ${selectedFaqIds.length} 条 FAQ。`}
                      actions={
                        <ActionToolbar>
                          <ActionButton disabled={submitting || selectedFaqIds.length === 0} onClick={() => void handleBulkStatusChange('ACTIVE')} tone="success">批量启用</ActionButton>
                          <ActionButton disabled={submitting || selectedFaqIds.length === 0} onClick={() => void handleBulkStatusChange('DISABLED')} tone="danger">批量停用</ActionButton>
                          <ActionButton disabled={submitting || selectedFaqIds.length === 0} onClick={() => void handleBulkStatusChange('DELETED')} variant="outline" tone="danger">批量删除</ActionButton>
                        </ActionToolbar>
                      }
                    />
                  }
                >
                    <Field label="所属机器人">
                      <Select
                        value={selectedChatbotId == null ? '' : String(selectedChatbotId)}
                        onChange={(value) => setSelectedChatbotId(value ? Number(value) : null)}
                        style={{ width: '100%' }}
                        options={[{ value: '', label: '请选择机器人' }, ...chatbots.map((chatbot) => ({ value: String(chatbot.id), label: chatbot.name }))]}
                      />
                    </Field>
                    <Field label="语言筛选">
                      <Input value={languageFilter} onChange={(event) => setLanguageFilter(event.target.value)} placeholder="如 zh-CN" style={inputStyle()} />
                    </Field>
                    <Field label="关键词">
                      <Input value={keywordFilter} onChange={(event) => setKeywordFilter(event.target.value)} placeholder="搜索问题或答案" style={inputStyle()} />
                    </Field>
                    <Field label="状态筛选">
                      <Select
                        value={statusFilter}
                        onChange={(value) => setStatusFilter(value as FaqSummary['status'] | '')}
                        style={{ width: '100%' }}
                        options={[
                          { value: '', label: '全部状态' },
                          { value: 'ACTIVE', label: '启用' },
                          { value: 'DISABLED', label: '停用' },
                          { value: 'DELETED', label: '已删除' }
                        ]}
                      />
                    </Field>
                </FilterSection>

                <ListSection title="FAQ 列表" description="详情通过弹窗打开，避免与列表同屏混排。">
                  <ListTable
                    rowKey="id"
                    dataSource={faqs}
                    loading={loading}
                    emptyText="当前机器人还没有 FAQ。"
                    selectedRowKeys={selectedFaqIds}
                    onSelectionChange={(keys) => setSelectedFaqIds(keys.map((key) => Number(key)))}
                    columns={[
                      {
                        key: 'question',
                        title: '问题',
                        render: (faq) => (
                          <TableLinkButton
                            onClick={() => {
                              setSelectedFaqId(faq.id);
                              setFaqDetailOpen(true);
                              setEditOpen(false);
                            }}
                          >
                            {faq.question}
                          </TableLinkButton>
                        )
                      },
                      { key: 'language', title: '语言', render: (faq) => faq.language },
                      {
                        key: 'status',
                        title: '状态',
                        render: (faq) => {
                          const badge = statusColor(faq.status);
                          return <StatusTag color={faq.status === 'ACTIVE' ? 'success' : badge.color === '#991b1b' ? 'error' : 'default'}>{badge.label}</StatusTag>;
                        }
                      },
                      {
                        key: 'actions',
                        title: '操作',
                        width: 220,
                        render: (faq) => (
                          <RowActionBar>
                            <RowActionButton onClick={() => { setSelectedFaqId(faq.id); setEditOpen(true); }}>编辑</RowActionButton>
                            <RowActionButton onClick={() => void handleStatusChange(faq.status === 'ACTIVE' ? 'DISABLED' : 'ACTIVE', faq)} disabled={submitting}>
                              {faq.status === 'ACTIVE' ? '停用' : '启用'}
                            </RowActionButton>
                            <RowActionButton onClick={() => void handleStatusChange('DELETED', faq)} disabled={submitting || faq.status === 'DELETED'} danger>删除</RowActionButton>
                          </RowActionBar>
                        )
                      }
                    ]}
                  />
                </ListSection>
              </div>
            )
          },
          {
            key: 'knowledge',
            label: '知识来源',
            children: (
              <div style={{ display: 'grid', gap: 16 }}>
                <FilterSection title="知识来源筛选" description="知识来源列表与详情分离，点击记录后以弹窗查看详情。" columns="repeat(2, minmax(0, 1fr))">
                  <Field label="所属机器人">
                    <Select
                      value={selectedChatbotId == null ? '' : String(selectedChatbotId)}
                      onChange={(value) => setSelectedChatbotId(value ? Number(value) : null)}
                      style={{ width: '100%' }}
                      options={[{ value: '', label: '请选择机器人' }, ...chatbots.map((chatbot) => ({ value: String(chatbot.id), label: chatbot.name }))]}
                    />
                  </Field>
                  <div style={{ display: 'grid', alignContent: 'end', color: '#64748b' }}>当前知识来源数：{knowledgeSources.length}</div>
                </FilterSection>

                <ListSection title="知识来源列表" description="展示文件与网页来源的状态、类型和基础元数据。">
                  <ListTable
                    rowKey="id"
                    dataSource={knowledgeSources}
                    loading={loading}
                    emptyText="当前机器人还没有知识来源。"
                    columns={[
                      {
                        key: 'sourceName',
                        title: '来源名称',
                        render: (source) => (
                          <TableLinkButton
                            onClick={() => {
                              setSelectedKnowledgeSourceId(source.id);
                              setKnowledgeDetailOpen(true);
                            }}
                          >
                            {source.sourceName}
                          </TableLinkButton>
                        )
                      },
                      { key: 'sourceType', title: '类型', render: (source) => <StatusTag color="orange">{source.sourceType}</StatusTag> },
                      { key: 'contentType', title: '内容类型', render: (source) => source.sourceType === 'FILE' ? source.contentType || source.sourceType : '网页来源' },
                      { key: 'sourceUri', title: '来源地址', render: (source) => source.sourceUri || '-' },
                      { key: 'fileSizeBytes', title: '大小', render: (source) => source.sourceType === 'FILE' ? `${Math.max(1, Math.ceil(source.fileSizeBytes / 1024))} KB` : '-' },
                      {
                        key: 'status',
                        title: '状态',
                        render: (source) => (
                          <StatusTag color={source.status === 'ACTIVE' ? 'success' : source.status === 'DELETED' ? 'default' : 'processing'}>
                            {knowledgeSourceStatusText(source.status)}
                          </StatusTag>
                        )
                      },
                      { key: 'createdAt', title: '上传时间', render: (source) => new Date(source.createdAt).toLocaleString() },
                      {
                        key: 'actions',
                        title: '操作',
                        width: 260,
                        render: (source) => (
                          <RowActionBar>
                            <RowActionButton onClick={() => { setSelectedKnowledgeSourceId(source.id); setKnowledgeDetailOpen(true); }}>查看</RowActionButton>
                            <RowActionButton onClick={() => { setSelectedKnowledgeSourceId(source.id); void handleKnowledgeRefresh(source.id); }} disabled={submitting}>刷新</RowActionButton>
                            <RowActionButton onClick={() => { setSelectedKnowledgeSourceId(source.id); void handleKnowledgeDelete(source.id); }} disabled={submitting || source.status === 'DELETED'} danger>删除</RowActionButton>
                          </RowActionBar>
                        )
                      }
                    ]}
                  />
                </ListSection>

                <div style={{ display: 'grid', gridTemplateColumns: 'minmax(320px, 0.95fr) minmax(320px, 1.05fr)', gap: 20 }}>
                  <FormCard title="文件知识源" description="上传租户知识文件，当前先支持元数据入库和额度限制校验。" submitLabel="上传文件" submitting={submitting} onSubmit={handleKnowledgeUpload} onCancel={() => setSelectedKnowledgeFile(null)}>
                    <Field label="选择文件">
                      <Upload
                        accept=".txt,.md,.pdf,.docx,.csv,.json"
                        beforeUpload={() => false}
                        maxCount={1}
                        fileList={selectedKnowledgeUploadList}
                        onChange={({ fileList }) => setSelectedKnowledgeFile(fileList[0]?.originFileObj ?? null)}
                        onRemove={() => {
                          setSelectedKnowledgeFile(null);
                          return true;
                        }}
                      >
                        <ActionButton variant="outline" tone="neutral">选择文件</ActionButton>
                      </Upload>
                    </Field>
                    <div style={{ color: '#64748b', fontSize: 14 }}>
                      {selectedKnowledgeFile
                        ? `待上传：${selectedKnowledgeFile.name} (${Math.ceil(selectedKnowledgeFile.size / 1024)} KB)`
                        : '请选择一个文件。支持 txt、md、pdf、docx、csv、json，单文件上限 10 MB。'}
                    </div>
                  </FormCard>

                  <FormCard title="网页知识源" description="录入公开网页地址，先保存来源信息，后续再接抓取与重抓。" submitLabel="创建网页来源" submitting={submitting} onSubmit={handleWebSourceCreate} onCancel={() => setWebSourceForm({ name: '', url: '' })}>
                    <Field label="来源名称">
                      <Input
                        value={webSourceForm.name}
                        onChange={(event) => setWebSourceForm((current) => ({ ...current, name: event.target.value }))}
                        placeholder="例如：帮助中心"
                        style={inputStyle()}
                      />
                    </Field>
                    <Field label="网页地址">
                      <Input
                        value={webSourceForm.url}
                        onChange={(event) => setWebSourceForm((current) => ({ ...current, url: event.target.value }))}
                        placeholder="https://example.com/help"
                        style={inputStyle()}
                      />
                    </Field>
                  </FormCard>
                </div>
              </div>
            )
          }
        ]}
      />

      <DetailModal open={importOpen} title="导入 FAQ" onCancel={() => setImportOpen(false)} width={860}>
        <FormCard title="导入 FAQ" description="粘贴 FAQ 导出 JSON，或仅提供 items 数组。系统会返回逐条失败原因。" submitLabel="开始导入" submitting={submitting} onSubmit={handleImport} onCancel={() => setImportOpen(false)}>
          <Field label="JSON 内容">
            <TextArea value={importPayload} onChange={(event) => setImportPayload(event.target.value)} autoSize={{ minRows: 8 }} style={inputStyle(true)} placeholder='{"items":[{"language":"zh-CN","question":"...","alternateQuestions":[],"answer":"..."}]}' />
          </Field>
          {importResult ? (
            <div style={{ display: 'grid', gap: 8, color: '#334155' }}>
              <div>成功导入：{importResult.importedCount}</div>
              <div>失败条数：{importResult.failures.length}</div>
              {importResult.failures.length > 0 ? (
                <div style={{ background: '#fff7ed', borderRadius: 14, padding: 12 }}>
                  {importResult.failures.map((failure) => (
                    <div key={`${failure.index}-${failure.field}`}>
                      第 {failure.index + 1} 条，字段 {failure.field}：{failure.reason}
                    </div>
                  ))}
                </div>
              ) : null}
            </div>
          ) : null}
        </FormCard>
      </DetailModal>

      <DetailModal open={createOpen} title="创建 FAQ" onCancel={() => setCreateOpen(false)} width={860}>
        <FormCard title="创建 FAQ" description="录入问题、相似问法和标准答案。" submitLabel="创建 FAQ" submitting={submitting} onSubmit={handleCreate} onCancel={() => setCreateOpen(false)}>
          <Field label="语言">
            <Input value={createForm.language} onChange={(event) => setCreateForm((current) => ({ ...current, language: event.target.value }))} style={inputStyle()} />
          </Field>
          <Field label="主问题">
            <Input value={createForm.question} onChange={(event) => setCreateForm((current) => ({ ...current, question: event.target.value }))} style={inputStyle()} />
          </Field>
          <Field label="相似问法（每行一条）">
            <TextArea value={createForm.alternateQuestions.join('\n')} onChange={(event) => setCreateForm((current) => ({ ...current, alternateQuestions: normalizeLines(event.target.value) }))} autoSize={{ minRows: 5 }} style={inputStyle(true)} />
          </Field>
          <Field label="答案">
            <TextArea value={createForm.answer} onChange={(event) => setCreateForm((current) => ({ ...current, answer: event.target.value }))} autoSize={{ minRows: 6 }} style={inputStyle(true)} />
          </Field>
        </FormCard>
      </DetailModal>

      <DetailModal open={faqDetailOpen && !!selectedFaq} title={selectedFaq ? `FAQ：${selectedFaq.question}` : 'FAQ 详情'} onCancel={() => setFaqDetailOpen(false)} width={860}>
        {!selectedFaq ? (
          <div style={{ color: '#64748b' }}>从 FAQ 列表中选择一项后再查看详情。</div>
        ) : (
          <div style={{ display: 'grid', gap: 16 }}>
            <SurfaceCard title="FAQ 详情" description="查看答案和相似问法，支持快速启停。">
              <div style={{ display: 'grid', gap: 10, color: '#334155' }}>
                <div>问题：{selectedFaq.question}</div>
                <div>语言：{selectedFaq.language}</div>
                <div>答案：{selectedFaq.answer}</div>
                <div>相似问法：{selectedFaq.alternateQuestions.length ? selectedFaq.alternateQuestions.join('；') : '暂无'}</div>
              </div>
              <ModalActionBar>
                <ActionButton onClick={() => setEditOpen(true)} variant="outline" tone="neutral">编辑 FAQ</ActionButton>
                <ActionButton disabled={submitting} onClick={() => void handleStatusChange('ACTIVE')} tone="success">启用</ActionButton>
                <ActionButton disabled={submitting} onClick={() => void handleStatusChange('DISABLED')} tone="danger">停用</ActionButton>
              </ModalActionBar>
            </SurfaceCard>
          </div>
        )}
      </DetailModal>

      <DetailModal open={editOpen && !!selectedFaq} title="编辑 FAQ" onCancel={() => setEditOpen(false)} width={860}>
        {selectedFaq ? (
          <FormCard title="编辑 FAQ" description="修改问题、相似问法和答案内容。" submitLabel="保存修改" submitting={submitting} onSubmit={handleEdit} onCancel={() => setEditOpen(false)}>
            <Field label="语言">
              <Input value={editForm.language} onChange={(event) => setEditForm((current) => ({ ...current, language: event.target.value }))} style={inputStyle()} />
            </Field>
            <Field label="主问题">
              <Input value={editForm.question} onChange={(event) => setEditForm((current) => ({ ...current, question: event.target.value }))} style={inputStyle()} />
            </Field>
            <Field label="相似问法（每行一条）">
              <TextArea value={editForm.alternateQuestions.join('\n')} onChange={(event) => setEditForm((current) => ({ ...current, alternateQuestions: normalizeLines(event.target.value) }))} autoSize={{ minRows: 5 }} style={inputStyle(true)} />
            </Field>
            <Field label="答案">
              <TextArea value={editForm.answer} onChange={(event) => setEditForm((current) => ({ ...current, answer: event.target.value }))} autoSize={{ minRows: 6 }} style={inputStyle(true)} />
            </Field>
          </FormCard>
        ) : null}
      </DetailModal>

      <DetailModal open={knowledgeDetailOpen && !!selectedKnowledgeSourceId} title={selectedKnowledgeSource ? `${selectedKnowledgeSource.sourceName} 详情` : '知识来源详情'} onCancel={() => setKnowledgeDetailOpen(false)} width={980}>
        {!selectedKnowledgeSource ? (
          <div style={{ color: '#64748b' }}>正在加载知识来源详情...</div>
        ) : (
          <div style={{ display: 'grid', gap: 16 }}>
            <SurfaceCard title="知识来源详情" description="查看来源状态、失败原因和已记录的元数据。">
              <ModalActionBar>
                <ActionButton onClick={() => void handleKnowledgeRefresh()} disabled={submitting} tone="neutral">刷新状态</ActionButton>
                <ActionButton onClick={() => void handleKnowledgeRetry()} disabled={submitting} variant="outline" tone="neutral">重试处理</ActionButton>
                <ActionButton onClick={() => void handleKnowledgeEnable()} disabled={submitting || selectedKnowledgeSource.status === 'ACTIVE' || selectedKnowledgeSource.status === 'DELETED'} tone="success">启用来源</ActionButton>
                <ActionButton onClick={() => void handleKnowledgeDisable()} disabled={submitting || selectedKnowledgeSource.status === 'DELETED'} variant="outline" tone="warning">停用来源</ActionButton>
                <ActionButton onClick={() => void handleKnowledgeDelete()} disabled={submitting || selectedKnowledgeSource.status === 'DELETED'} tone="danger">删除来源</ActionButton>
              </ModalActionBar>
              <div style={{ display: 'grid', gap: 12, color: '#334155' }}>
                <div>名称：{selectedKnowledgeSource.sourceName}</div>
                <div>类型：{selectedKnowledgeSource.sourceType}</div>
                <div>状态：{selectedKnowledgeSource.status}</div>
                <div>来源：{selectedKnowledgeSource.sourceUri || '无'}</div>
                <div>失败原因：{selectedKnowledgeSource.failureReason || '无'}</div>
                <div>创建时间：{new Date(selectedKnowledgeSource.createdAt).toLocaleString()}</div>
                <div>
                  <strong>Chunk 预览</strong>
                </div>
                {selectedKnowledgeSource.chunks.length === 0 ? (
                  <div>当前还没有生成 chunk，请先刷新或重试处理。</div>
                ) : (
                  <div style={{ display: 'grid', gap: 10 }}>
                    {selectedKnowledgeSource.chunks.map((chunk) => (
                      <div key={chunk.id} style={{ borderRadius: 14, background: '#f8fafc', padding: 12 }}>
                        <div style={{ fontWeight: 700, marginBottom: 6 }}>Chunk #{chunk.chunkIndex + 1}</div>
                        <div style={{ color: '#475569', marginBottom: 6 }}>{chunk.summary || '无摘要'}</div>
                        <div style={{ whiteSpace: 'pre-wrap', fontSize: 14 }}>{chunk.content}</div>
                      </div>
                    ))}
                  </div>
                )}
                <div>
                  <strong>元数据</strong>
                </div>
                {Object.keys(selectedKnowledgeSource.metadata).length === 0 ? (
                  <div>暂无元数据。</div>
                ) : (
                  <div style={{ display: 'grid', gap: 8 }}>
                    {Object.entries(selectedKnowledgeSource.metadata).map(([key, value]) => (
                      <div key={key}>
                        {key}：{value}
                      </div>
                    ))}
                  </div>
                )}
              </div>
            </SurfaceCard>
          </div>
        )}
      </DetailModal>
    </>
  );
}
