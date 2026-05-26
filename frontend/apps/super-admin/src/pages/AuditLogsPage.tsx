import type { FormEvent } from 'react';
import { useEffect, useState } from 'react';

import { Input, Select } from 'antd';
import { DetailModal, FilterFieldsGrid, ListSection, ListTable, NoticeBanner, PageStack, RowActionBar, RowActionButton, SectionHeader, StatCard, StatusTag, SurfaceCard, TableLinkButton } from '@agentx/admin-ui';
import { getAuditLog, listAuditLogs, type AuditLogDetail, type AuditLogSummary, type AuthSession } from '@agentx/api-client';

import { Field, inputStyle, TenantFormCard } from '../form-ui';

function auditResultText(result: string) {
  if (result === 'SUCCESS') {
    return '成功';
  }

  if (result === 'FAILED') {
    return '失败';
  }

  if (result === 'PARTIAL_SUCCESS') {
    return '部分成功';
  }

  return result;
}

function auditRiskText(riskLevel: string) {
  if (riskLevel === 'LOW') {
    return '低';
  }

  if (riskLevel === 'MEDIUM') {
    return '中';
  }

  if (riskLevel === 'HIGH') {
    return '高';
  }

  return riskLevel;
}

export function AuditLogsPage({ session }: { session: AuthSession }) {
  const token = session.accessToken;
  const [logs, setLogs] = useState<AuditLogSummary[]>([]);
  const [selectedLogId, setSelectedLogId] = useState<number | null>(null);
  const [selectedLog, setSelectedLog] = useState<AuditLogDetail | null>(null);
  const [detailOpen, setDetailOpen] = useState(false);
  const [loadingList, setLoadingList] = useState(true);
  const [loadingDetail, setLoadingDetail] = useState(false);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);
  const [filters, setFilters] = useState({
    tenantId: '',
    actorUserId: '',
    actionType: '',
    result: '',
    riskLevel: '',
    createdFrom: '',
    createdTo: ''
  });

  const filteredHighRiskCount = logs.filter((log) => log.riskLevel === 'HIGH').length;
  const filteredSuccessCount = logs.filter((log) => log.result === 'SUCCESS').length;

  const loadLogs = async () => {
    setLoadingList(true);
    setErrorMessage(null);

    try {
      const nextLogs = await listAuditLogs(token, {
        tenantId: filters.tenantId ? Number(filters.tenantId) : undefined,
        actorUserId: filters.actorUserId ? Number(filters.actorUserId) : undefined,
        actionType: filters.actionType || undefined,
        result: filters.result || undefined,
        riskLevel: filters.riskLevel || undefined,
        createdFrom: filters.createdFrom ? new Date(filters.createdFrom).toISOString() : undefined,
        createdTo: filters.createdTo ? new Date(filters.createdTo).toISOString() : undefined
      });
      setLogs(nextLogs);
      setSelectedLogId((currentSelectedLogId) => (nextLogs.some((log) => log.id === currentSelectedLogId) ? currentSelectedLogId : null));
    } catch (error) {
      setErrorMessage(error instanceof Error ? error.message : '审计日志加载失败。');
    } finally {
      setLoadingList(false);
    }
  };

  useEffect(() => {
    void loadLogs();
  }, [token]);

  useEffect(() => {
    if (!selectedLogId) {
      setSelectedLog(null);
      return;
    }

    let cancelled = false;

    const loadDetail = async () => {
      setLoadingDetail(true);
      setErrorMessage(null);

      try {
        const detail = await getAuditLog(token, selectedLogId);
        if (!cancelled) {
          setSelectedLog(detail);
        }
      } catch (error) {
        if (!cancelled) {
          setErrorMessage(error instanceof Error ? error.message : '审计详情加载失败。');
        }
      } finally {
        if (!cancelled) {
          setLoadingDetail(false);
        }
      }
    };

    void loadDetail();

    return () => {
      cancelled = true;
    };
  }, [selectedLogId, token]);

  const handleFilterSubmit = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    await loadLogs();
  };

  const handleResetFilters = async () => {
    setFilters({ tenantId: '', actorUserId: '', actionType: '', result: '', riskLevel: '', createdFrom: '', createdTo: '' });

    setLoadingList(true);
    setErrorMessage(null);

    try {
      const nextLogs = await listAuditLogs(token, {});
      setLogs(nextLogs);
      setSelectedLogId(null);
    } catch (error) {
      setErrorMessage(error instanceof Error ? error.message : '审计日志加载失败。');
    } finally {
      setLoadingList(false);
    }
  };

  return (
    <>
      <SectionHeader title="审计日志" />

      <PageStack>
        {errorMessage ? <NoticeBanner tone="error">{errorMessage}</NoticeBanner> : null}
      </PageStack>

      <PageStack>
        <SurfaceCard title="筛选条件" description="查询条件和列表操作都留在同一页面，不再单独拆操作页签。">
          <TenantFormCard
            title="筛选条件"
            description="按时间、用户、动作、结果和租户筛选审计记录。"
            submitLabel="应用筛选"
            submitting={loadingList}
            onSubmit={handleFilterSubmit}
            onCancel={() => {
              void handleResetFilters();
            }}
          >
            <>
              <FilterFieldsGrid columns="repeat(3, minmax(0, 1fr))">
              <Field label="租户 ID"><Input value={filters.tenantId} onChange={(event) => setFilters((current) => ({ ...current, tenantId: event.target.value }))} style={inputStyle()} /></Field>
              <Field label="操作人 ID"><Input value={filters.actorUserId} onChange={(event) => setFilters((current) => ({ ...current, actorUserId: event.target.value }))} style={inputStyle()} /></Field>
              <Field label="动作类型"><Input value={filters.actionType} onChange={(event) => setFilters((current) => ({ ...current, actionType: event.target.value }))} placeholder="例如 TENANT_CREATED" style={inputStyle()} /></Field>
              <Field label="结果">
                <Select value={filters.result} onChange={(value) => setFilters((current) => ({ ...current, result: value }))} style={{ width: '100%' }} options={[{ value: '', label: '全部结果' }, { value: 'SUCCESS', label: auditResultText('SUCCESS') }, { value: 'FAILED', label: auditResultText('FAILED') }, { value: 'PARTIAL_SUCCESS', label: auditResultText('PARTIAL_SUCCESS') }]} />
              </Field>
              <Field label="风险级别">
                <Select value={filters.riskLevel} onChange={(value) => setFilters((current) => ({ ...current, riskLevel: value }))} style={{ width: '100%' }} options={[{ value: '', label: '全部风险' }, { value: 'LOW', label: auditRiskText('LOW') }, { value: 'MEDIUM', label: auditRiskText('MEDIUM') }, { value: 'HIGH', label: auditRiskText('HIGH') }]} />
              </Field>
              <Field label="开始时间"><Input type="datetime-local" value={filters.createdFrom} onChange={(event) => setFilters((current) => ({ ...current, createdFrom: event.target.value }))} style={inputStyle()} /></Field>
              <Field label="结束时间"><Input type="datetime-local" value={filters.createdTo} onChange={(event) => setFilters((current) => ({ ...current, createdTo: event.target.value }))} style={inputStyle()} /></Field>
              </FilterFieldsGrid>
            </>
          </TenantFormCard>
        </SurfaceCard>

        <div style={{ display: 'grid', gridTemplateColumns: 'repeat(3, minmax(0, 1fr))', gap: 16 }}>
          <StatCard title="当前结果数" value={String(logs.length)} description="基于当前筛选条件返回。" />
          <StatCard title="高风险条数" value={String(filteredHighRiskCount)} description="当前结果集中的 HIGH 风险日志。" />
          <StatCard title="成功率" value={logs.length === 0 ? '-' : `${Math.round((filteredSuccessCount / logs.length) * 100)}%`} description="按当前结果集的 result=SUCCESS 计算。" />
        </div>

        <ListSection title="日志列表" description="点击动作列查看详情，末列保留查看操作。">
          <ListTable
            rowKey="id"
            dataSource={logs}
            loading={loadingList}
            emptyText="当前筛选条件下没有审计日志。"
            columns={[
              {
                key: 'actionType',
                title: '动作',
                render: (log) => (
                  <TableLinkButton
                    onClick={() => {
                      setSelectedLogId(log.id);
                      setDetailOpen(true);
                    }}
                  >
                    {log.actionType}
                  </TableLinkButton>
                )
              },
              { key: 'result', title: '结果', render: (log) => <StatusTag color={log.result === 'SUCCESS' ? 'success' : 'error'}>{auditResultText(log.result)}</StatusTag> },
              { key: 'risk', title: '风险', render: (log) => <StatusTag color={log.riskLevel === 'HIGH' ? 'error' : log.riskLevel === 'MEDIUM' ? 'warning' : 'processing'}>{auditRiskText(log.riskLevel)}</StatusTag> },
              { key: 'target', title: '目标', render: (log) => `${log.targetType} ${log.targetId ?? '-'}` },
              { key: 'tenant', title: '租户', render: (log) => log.tenantId ?? '平台级' },
              { key: 'actor', title: '操作人', render: (log) => log.actorUserId ?? '匿名' },
              { key: 'createdAt', title: '时间', render: (log) => new Date(log.createdAt).toLocaleString() },
              {
                key: 'actions',
                title: '操作',
                width: 120,
                render: (log) => (
                  <RowActionBar>
                    <RowActionButton
                      onClick={() => {
                        setSelectedLogId(log.id);
                        setDetailOpen(true);
                      }}
                    >
                      查看
                    </RowActionButton>
                  </RowActionBar>
                )
              }
            ]}
          />
        </ListSection>
      </PageStack>

      <DetailModal open={detailOpen && !!selectedLogId} title={selectedLog ? `${selectedLog.actionType} 详情` : '日志详情'} onCancel={() => setDetailOpen(false)} width={860}>
        {loadingDetail ? (
          <div style={{ color: '#64748b' }}>详情加载中...</div>
        ) : !selectedLog ? (
          <div style={{ color: '#64748b' }}>从日志列表中选择一条审计日志查看详情。</div>
        ) : (
          <div style={{ display: 'grid', gap: 16 }}>
            <div style={{ display: 'grid', gap: 10, color: '#334155' }}>
              <div>动作：{selectedLog.actionType}</div>
              <div>结果：{auditResultText(selectedLog.result)}</div>
              <div>风险级别：{auditRiskText(selectedLog.riskLevel)}</div>
              <div>租户 ID：{selectedLog.tenantId ?? '平台级'}</div>
              <div>操作人 ID：{selectedLog.actorUserId ?? '匿名'}</div>
              <div>目标类型：{selectedLog.targetType}</div>
              <div>目标 ID：{selectedLog.targetId ?? '-'}</div>
              <div>时间：{new Date(selectedLog.createdAt).toLocaleString()}</div>
            </div>

            <div style={{ borderTop: '1px solid #e2e8f0', paddingTop: 16 }}>
              <strong>上下文</strong>
              <pre
                style={{
                  marginTop: 12,
                  marginBottom: 0,
                  borderRadius: 16,
                  background: '#0f172a',
                  color: '#e2e8f0',
                  padding: 16,
                  overflowX: 'auto',
                  fontSize: 12,
                  lineHeight: 1.5
                }}
              >
                {JSON.stringify(selectedLog.context, null, 2)}
              </pre>
            </div>
          </div>
        )}
      </DetailModal>
    </>
  );
}
