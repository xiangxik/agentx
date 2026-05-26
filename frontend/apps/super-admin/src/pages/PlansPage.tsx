import type { FormEvent } from 'react';
import { useEffect, useState } from 'react';

import { Input, InputNumber, Select } from 'antd';
import { ActionButton, DetailModal, DetailSummaryHeader, ListSection, ListTable, ModalActionBar, NoticeBanner, PageStack, RowActionBar, RowActionButton, SectionHeader, StatCard, StatusTag, SurfaceCard, TableLinkButton } from '@agentx/admin-ui';
import {
  assignTenantPlan,
  createPlan,
  getTenantPlanAssignment,
  listPlans,
  listTenants,
  type AuthSession,
  type CreatePlanRequest,
  type PlanSummary,
  type TenantSummary,
  updatePlan,
  updatePlanStatus,
  type UpdatePlanRequest
} from '@agentx/api-client';

import { Field, inputStyle, TenantFormCard } from '../form-ui';

type PlanCreateFormState = {
  code: string;
  name: string;
  limits: Record<string, string>;
};

type PlanUpdateFormState = {
  name: string;
  limits: Record<string, string>;
};

const quotaFields = [
  { key: 'chatbots', label: '机器人数量' },
  { key: 'messages', label: '消息数' },
  { key: 'tokens', label: 'Token 数' },
  { key: 'files', label: '文件数' },
  { key: 'storageMb', label: '存储(MB)' }
] as const;

function normalizeFormValue(value: string) {
  return value.trim();
}

function buildLimitFormState(limits?: Record<string, number>) {
  return Object.fromEntries(quotaFields.map((field) => [field.key, limits?.[field.key] == null ? '' : String(limits[field.key])]));
}

function parseLimitFormState(limits: Record<string, string>) {
  return quotaFields.reduce<Record<string, number>>((result, field) => {
    const rawValue = limits[field.key]?.trim();

    if (!rawValue) {
      return result;
    }

    result[field.key] = Number(rawValue);
    return result;
  }, {});
}

function planStatusLabel(status: PlanSummary['status']) {
  return status === 'ACTIVE' ? '已启用' : '已停用';
}

export function PlansPage({ session }: { session: AuthSession }) {
  const token = session.accessToken;
  const [plans, setPlans] = useState<PlanSummary[]>([]);
  const [tenants, setTenants] = useState<TenantSummary[]>([]);
  const [selectedAssignment, setSelectedAssignment] = useState<{ tenantId: number; planId: number; overrides: Record<string, number> } | null>(null);
  const [loadingAssignment, setLoadingAssignment] = useState(false);
  const [selectedPlanId, setSelectedPlanId] = useState<number | null>(null);
  const [detailOpen, setDetailOpen] = useState(false);
  const [loading, setLoading] = useState(true);
  const [submitting, setSubmitting] = useState(false);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);
  const [notice, setNotice] = useState<string | null>(null);
  const [createOpen, setCreateOpen] = useState(false);
  const [editOpen, setEditOpen] = useState(false);
  const [createForm, setCreateForm] = useState<PlanCreateFormState>({ code: '', name: '', limits: buildLimitFormState() });
  const [editForm, setEditForm] = useState<PlanUpdateFormState>({ name: '', limits: buildLimitFormState() });
  const [assignmentTenantId, setAssignmentTenantId] = useState('');
  const [assignmentPlanId, setAssignmentPlanId] = useState('');
  const [assignmentOverrides, setAssignmentOverrides] = useState<Record<string, string>>(buildLimitFormState());

  const selectedPlan = plans.find((plan) => plan.id === selectedPlanId) ?? null;

  const loadData = async (preferredPlanId?: number) => {
    setLoading(true);
    setErrorMessage(null);

    try {
      const [nextPlans, nextTenants] = await Promise.all([listPlans(token), listTenants(token)]);
      setPlans(nextPlans);
      setTenants(nextTenants);
      const nextSelectedPlanId =
        preferredPlanId ??
        (nextPlans.some((plan) => plan.id === selectedPlanId) ? selectedPlanId : null);
      setSelectedPlanId(nextSelectedPlanId ?? null);
      if (!assignmentTenantId && nextTenants[0]) {
        setAssignmentTenantId(String(nextTenants[0].id));
      }
      if (!assignmentPlanId && nextPlans[0]) {
        setAssignmentPlanId(String(nextPlans[0].id));
      }
    } catch (error) {
      setErrorMessage(error instanceof Error ? error.message : '套餐数据加载失败。');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    void loadData();
  }, [token]);

  useEffect(() => {
    if (!selectedPlan) {
      setEditForm({ name: '', limits: buildLimitFormState() });
      return;
    }

    setEditForm({ name: selectedPlan.name, limits: buildLimitFormState(selectedPlan.limits) });

    if (!assignmentPlanId) {
      setAssignmentPlanId(String(selectedPlan.id));
    }
  }, [assignmentPlanId, selectedPlan]);

  useEffect(() => {
    if (!assignmentTenantId) {
      setSelectedAssignment(null);
      return;
    }

    let cancelled = false;

    const loadAssignment = async () => {
      setLoadingAssignment(true);

      try {
        const assignment = await getTenantPlanAssignment(token, Number(assignmentTenantId));

        if (cancelled) {
          return;
        }

        setSelectedAssignment(assignment);
        setAssignmentPlanId(String(assignment.planId));
        setAssignmentOverrides(buildLimitFormState(assignment.overrides));
      } catch {
        if (!cancelled) {
          setSelectedAssignment(null);
          setAssignmentOverrides(buildLimitFormState());
        }
      } finally {
        if (!cancelled) {
          setLoadingAssignment(false);
        }
      }
    };

    void loadAssignment();

    return () => {
      cancelled = true;
    };
  }, [assignmentTenantId, token]);

  const updatePlanInList = (nextPlan: PlanSummary) => {
    setPlans((currentPlans) => currentPlans.map((plan) => (plan.id === nextPlan.id ? nextPlan : plan)));
  };

  const handleCreatePlan = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    setSubmitting(true);
    setErrorMessage(null);
    setNotice(null);

    try {
      const createdPlan = await createPlan(token, {
        code: normalizeFormValue(createForm.code),
        name: normalizeFormValue(createForm.name),
        limits: parseLimitFormState(createForm.limits)
      } as CreatePlanRequest);
      setCreateOpen(false);
      setCreateForm({ code: '', name: '', limits: buildLimitFormState() });
      setNotice(`套餐 ${createdPlan.name} 已创建。`);
      await loadData(createdPlan.id);
      setAssignmentPlanId(String(createdPlan.id));
    } catch (error) {
      setErrorMessage(error instanceof Error ? error.message : '套餐创建失败。');
    } finally {
      setSubmitting(false);
    }
  };

  const handleEditPlan = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    if (!selectedPlan) {
      return;
    }

    setSubmitting(true);
    setErrorMessage(null);
    setNotice(null);

    try {
      const updatedPlan = await updatePlan(token, selectedPlan.id, {
        name: normalizeFormValue(editForm.name),
        limits: parseLimitFormState(editForm.limits)
      } as UpdatePlanRequest);
      updatePlanInList(updatedPlan);
      setEditOpen(false);
      setNotice(`套餐 ${updatedPlan.name} 已更新。`);
    } catch (error) {
      setErrorMessage(error instanceof Error ? error.message : '套餐更新失败。');
    } finally {
      setSubmitting(false);
    }
  };

  const handlePlanStatusChange = async (nextStatus: PlanSummary['status']) => {
    if (!selectedPlan) {
      return;
    }

    const confirmed =
      typeof window === 'undefined'
        ? true
        : window.confirm(nextStatus === 'DISABLED' ? '停用后新租户将不应继续分配到该套餐，是否继续？' : '确认重新启用该套餐？');

    if (!confirmed) {
      return;
    }

    setSubmitting(true);
    setErrorMessage(null);
    setNotice(null);

    try {
      const updatedPlan = await updatePlanStatus(token, selectedPlan.id, nextStatus);
      updatePlanInList(updatedPlan);
      setNotice(nextStatus === 'DISABLED' ? `套餐 ${updatedPlan.name} 已停用。` : `套餐 ${updatedPlan.name} 已启用。`);
    } catch (error) {
      setErrorMessage(error instanceof Error ? error.message : '套餐状态更新失败。');
    } finally {
      setSubmitting(false);
    }
  };

  const handleAssignmentSubmit = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();

    if (!assignmentTenantId || !assignmentPlanId) {
      setErrorMessage('请选择租户和套餐后再提交分配。');
      return;
    }

    setSubmitting(true);
    setErrorMessage(null);
    setNotice(null);

    try {
      const nextAssignment = await assignTenantPlan(token, {
        tenantId: Number(assignmentTenantId),
        planId: Number(assignmentPlanId),
        overrides: parseLimitFormState(assignmentOverrides)
      });
      setSelectedAssignment(nextAssignment);

      const tenantName = tenants.find((tenant) => tenant.id === Number(assignmentTenantId))?.name ?? `#${assignmentTenantId}`;
      const planName = plans.find((plan) => plan.id === Number(assignmentPlanId))?.name ?? `#${assignmentPlanId}`;
      setNotice(`已为租户 ${tenantName} 分配套餐 ${planName}。`);
    } catch (error) {
      setErrorMessage(error instanceof Error ? error.message : '套餐分配失败。');
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <>
      <SectionHeader
        title="套餐与额度"
        actions={
          <ActionButton onClick={() => {
              setCreateOpen(true);
              setEditOpen(false);
              setNotice(null);
            }}>
            新建套餐
          </ActionButton>
        }
      />

      <PageStack>
        {errorMessage ? <NoticeBanner tone="error">{errorMessage}</NoticeBanner> : null}
        {notice ? <NoticeBanner tone="notice">{notice}</NoticeBanner> : null}
      </PageStack>

      <PageStack>
        <ListSection title="套餐列表" description="列表页直接展示关键配额列，点击套餐名称查看详情，操作列处理编辑和启停。">
          <ListTable
            rowKey="id"
            dataSource={plans}
            loading={loading}
            emptyText="还没有套餐，先创建一个基础套餐。"
            columns={[
              {
                key: 'name',
                title: '套餐名称',
                render: (plan) => (
                  <TableLinkButton
                    onClick={() => {
                      setSelectedPlanId(plan.id);
                      setDetailOpen(true);
                      setEditOpen(false);
                      setNotice(null);
                    }}
                  >
                    {plan.name}
                  </TableLinkButton>
                )
              },
              { key: 'code', title: '编码', render: (plan) => plan.code },
              ...quotaFields.map((field) => ({
                key: field.key,
                title: field.label,
                render: (plan: PlanSummary) => plan.limits[field.key] ?? '-'
              })),
              {
                key: 'status',
                title: '状态',
                render: (plan) => <StatusTag color={plan.status === 'ACTIVE' ? 'success' : 'default'}>{planStatusLabel(plan.status)}</StatusTag>
              },
              {
                key: 'actions',
                title: '操作',
                width: 220,
                render: (plan) => (
                  <RowActionBar>
                    <RowActionButton
                      onClick={() => {
                        setSelectedPlanId(plan.id);
                        setEditOpen(true);
                        setDetailOpen(false);
                      }}
                    >
                      编辑
                    </RowActionButton>
                    <RowActionButton
                      onClick={() => {
                        setSelectedPlanId(plan.id);
                        void handlePlanStatusChange(plan.status === 'ACTIVE' ? 'DISABLED' : 'ACTIVE');
                      }}
                      danger={plan.status === 'ACTIVE'}
                      disabled={submitting}
                    >
                      {plan.status === 'ACTIVE' ? '停用' : '启用'}
                    </RowActionButton>
                  </RowActionBar>
                )
              }
            ]}
          />
        </ListSection>

        <SurfaceCard title="租户套餐分配" description="批量性分配动作收回到列表页内，与套餐列表同屏协作。">
          <TenantFormCard
            title="租户套餐分配"
            description="为指定租户分配套餐，并按资源维度设置覆盖值。留空表示不覆盖。"
            submitLabel="保存分配"
            submitting={submitting}
            onSubmit={handleAssignmentSubmit}
            onCancel={() => {
              setAssignmentOverrides(buildLimitFormState());
              setNotice(null);
            }}
          >
            <>
              <Field label="租户">
                <Select
                  value={assignmentTenantId}
                  onChange={(value) => {
                    setAssignmentTenantId(value);
                    setNotice(null);
                  }}
                  style={{ width: '100%' }}
                  options={[{ value: '', label: '请选择租户' }, ...tenants.map((tenant) => ({ value: String(tenant.id), label: tenant.name }))]}
                />
              </Field>
              <div style={{ borderRadius: 16, background: '#f8fafc', padding: 14, color: '#334155' }}>
                {loadingAssignment ? (
                  '正在加载当前套餐分配...'
                ) : !assignmentTenantId ? (
                  '请选择租户后查看当前分配。'
                ) : !selectedAssignment ? (
                  '该租户当前还没有分配套餐。'
                ) : (
                  <>
                    当前套餐：{plans.find((plan) => plan.id === selectedAssignment.planId)?.name ?? `#${selectedAssignment.planId}`}
                    <br />
                    覆盖项：
                    {Object.keys(selectedAssignment.overrides).length === 0
                      ? '无，沿用套餐默认值'
                      : quotaFields
                          .filter((field) => selectedAssignment.overrides[field.key] != null)
                          .map((field) => `${field.label} ${selectedAssignment.overrides[field.key]}`)
                          .join('，')}
                  </>
                )}
              </div>
              <Field label="套餐">
                <Select value={assignmentPlanId} onChange={(value) => setAssignmentPlanId(value)} style={{ width: '100%' }} options={[{ value: '', label: '请选择套餐' }, ...plans.map((plan) => ({ value: String(plan.id), label: plan.name }))]} />
              </Field>
              {quotaFields.map((field) => (
                <Field key={field.key} label={`${field.label} 覆盖值`}>
                  <InputNumber
                    min="0"
                    stringMode
                    value={assignmentOverrides[field.key]}
                    onChange={(value) =>
                      setAssignmentOverrides((current) => ({
                        ...current,
                        [field.key]: value == null ? '' : String(value)
                      }))
                    }
                    placeholder="留空表示沿用套餐默认值"
                    style={{ width: '100%' }}
                  />
                </Field>
              ))}
            </>
          </TenantFormCard>
        </SurfaceCard>
      </PageStack>

      <DetailModal open={detailOpen && !!selectedPlan} title={selectedPlan ? `${selectedPlan.name} 详情` : '套餐详情'} onCancel={() => setDetailOpen(false)} width={760}>
        {!selectedPlan ? (
          <div style={{ color: '#64748b' }}>从套餐列表中选择一项后再查看详情。</div>
        ) : (
          <div style={{ display: 'grid', gap: 16 }}>
            <DetailSummaryHeader
              title={selectedPlan.name}
              subtitle={selectedPlan.code}
              status={<StatusTag color={selectedPlan.status === 'ACTIVE' ? 'success' : 'default'}>{planStatusLabel(selectedPlan.status)}</StatusTag>}
            />

            <div style={{ display: 'grid', gap: 10 }}>
              {quotaFields.map((field) => (
                <div key={field.key} style={{ display: 'flex', justifyContent: 'space-between', gap: 12, color: '#334155' }}>
                  <span>{field.label}</span>
                  <strong>{selectedPlan.limits[field.key] ?? '-'}</strong>
                </div>
              ))}
            </div>

            <ModalActionBar>
              <ActionButton onClick={() => { setEditOpen(true); setDetailOpen(false); }} variant="outline" tone="neutral">编辑套餐</ActionButton>
              <ActionButton disabled={submitting || selectedPlan.status === 'ACTIVE'} onClick={() => void handlePlanStatusChange('ACTIVE')} tone="primary">启用套餐</ActionButton>
              <ActionButton disabled={submitting || selectedPlan.status === 'DISABLED'} onClick={() => void handlePlanStatusChange('DISABLED')} tone="danger">停用套餐</ActionButton>
            </ModalActionBar>
          </div>
        )}
      </DetailModal>

      <DetailModal open={createOpen} title="创建套餐" onCancel={() => setCreateOpen(false)} width={760}>
        <TenantFormCard title="创建套餐" description="定义默认限额，后续可在租户维度做覆盖。" submitLabel="创建套餐" submitting={submitting} onSubmit={handleCreatePlan} onCancel={() => { setCreateOpen(false); setCreateForm({ code: '', name: '', limits: buildLimitFormState() }); }}>
          <>
            <Field label="套餐编码">
              <Input value={createForm.code} onChange={(event) => setCreateForm((current) => ({ ...current, code: event.target.value }))} required placeholder="starter" style={inputStyle()} />
            </Field>
            <Field label="套餐名称">
              <Input value={createForm.name} onChange={(event) => setCreateForm((current) => ({ ...current, name: event.target.value }))} required placeholder="Starter" style={inputStyle()} />
            </Field>
            {quotaFields.map((field) => (
              <Field key={field.key} label={field.label}>
                <InputNumber
                  min="0"
                  stringMode
                  value={createForm.limits[field.key]}
                  onChange={(value) =>
                    setCreateForm((current) => ({
                      ...current,
                      limits: { ...current.limits, [field.key]: value == null ? '' : String(value) }
                    }))
                  }
                  style={{ width: '100%' }}
                />
              </Field>
            ))}
          </>
        </TenantFormCard>
      </DetailModal>

      <DetailModal open={editOpen && !!selectedPlan} title="编辑套餐" onCancel={() => setEditOpen(false)} width={760}>
        {selectedPlan ? (
          <TenantFormCard title="编辑套餐" description="更新套餐名称和默认限额。" submitLabel="保存套餐" submitting={submitting} onSubmit={handleEditPlan} onCancel={() => setEditOpen(false)}>
            <>
              <Field label="套餐名称">
                <Input value={editForm.name} onChange={(event) => setEditForm((current) => ({ ...current, name: event.target.value }))} required style={inputStyle()} />
              </Field>
              {quotaFields.map((field) => (
                <Field key={field.key} label={field.label}>
                  <InputNumber
                    min="0"
                    stringMode
                    value={editForm.limits[field.key]}
                    onChange={(value) =>
                      setEditForm((current) => ({
                        ...current,
                        limits: { ...current.limits, [field.key]: value == null ? '' : String(value) }
                      }))
                    }
                    style={{ width: '100%' }}
                  />
                </Field>
              ))}
            </>
          </TenantFormCard>
        ) : null}
      </DetailModal>
    </>
  );
}
