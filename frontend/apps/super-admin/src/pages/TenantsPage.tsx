import type { FormEvent } from 'react';
import { useEffect, useState } from 'react';

import { Input } from 'antd';
import { ActionButton, DetailModal, DetailSummaryHeader, ListSection, ListTable, ModalActionBar, NoticeBanner, PageStack, RowActionBar, RowActionButton, SectionHeader, StatusTag, SurfaceCard, TableLinkButton } from '@agentx/admin-ui';
import {
  createTenant,
  getTenant,
  listTenants,
  type AuthSession,
  type CreateTenantRequest,
  type TenantDetail,
  type TenantSummary,
  type UpdateTenantRequest,
  updateTenant,
  updateTenantStatus
} from '@agentx/api-client';

import { Field, inputStyle, TenantFormCard } from '../form-ui';

const { TextArea, Password } = Input;

type TenantCreateFormState = CreateTenantRequest;
type TenantUpdateFormState = UpdateTenantRequest;

const emptyCreateForm = (): TenantCreateFormState => ({
  code: '',
  name: '',
  contactName: '',
  contactEmail: '',
  notes: '',
  adminEmail: '',
  adminDisplayName: '',
  adminPassword: ''
});

const emptyUpdateForm = (): TenantUpdateFormState => ({
  name: '',
  contactName: '',
  contactEmail: '',
  notes: ''
});

function normalizeFormValue(value: string) {
  return value.trim();
}

function normalizeOptionalValue(value: string) {
  return normalizeFormValue(value);
}

function statusLabel(status: TenantSummary['status']) {
  return status === 'ACTIVE' ? '启用中' : '已停用';
}

export function TenantsPage({ session }: { session: AuthSession }) {
  const token = session.accessToken;
  const [tenants, setTenants] = useState<TenantSummary[]>([]);
  const [selectedTenantId, setSelectedTenantId] = useState<number | null>(null);
  const [selectedTenant, setSelectedTenant] = useState<TenantDetail | null>(null);
  const [detailOpen, setDetailOpen] = useState(false);
  const [loadingList, setLoadingList] = useState(true);
  const [loadingDetail, setLoadingDetail] = useState(false);
  const [submitting, setSubmitting] = useState(false);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);
  const [notice, setNotice] = useState<string | null>(null);
  const [createOpen, setCreateOpen] = useState(false);
  const [editOpen, setEditOpen] = useState(false);
  const [createForm, setCreateForm] = useState<TenantCreateFormState>(emptyCreateForm);
  const [editForm, setEditForm] = useState<TenantUpdateFormState>(emptyUpdateForm);

  const refreshList = async (preferredTenantId?: number) => {
    setLoadingList(true);
    setErrorMessage(null);

    try {
      const nextTenants = await listTenants(token);
      setTenants(nextTenants);
      const nextSelectedTenantId =
        preferredTenantId ??
        (nextTenants.some((tenant) => tenant.id === selectedTenantId) ? selectedTenantId : null);
      setSelectedTenantId(nextSelectedTenantId ?? null);
    } catch (error) {
      setErrorMessage(error instanceof Error ? error.message : '租户列表加载失败。');
    } finally {
      setLoadingList(false);
    }
  };

  useEffect(() => {
    void refreshList();
  }, [token]);

  useEffect(() => {
    if (!selectedTenantId) {
      setSelectedTenant(null);
      setEditOpen(false);
      return;
    }

    let cancelled = false;

    const fetchDetail = async () => {
      setLoadingDetail(true);
      setErrorMessage(null);

      try {
        const detail = await getTenant(token, selectedTenantId);
        if (cancelled) {
          return;
        }
        setSelectedTenant(detail);
        setEditForm({
          name: detail.name,
          contactName: detail.contactName ?? '',
          contactEmail: detail.contactEmail ?? '',
          notes: detail.notes ?? ''
        });
      } catch (error) {
        if (!cancelled) {
          setErrorMessage(error instanceof Error ? error.message : '租户详情加载失败。');
        }
      } finally {
        if (!cancelled) {
          setLoadingDetail(false);
        }
      }
    };

    void fetchDetail();

    return () => {
      cancelled = true;
    };
  }, [selectedTenantId, token]);

  const updateTenantSummary = (nextSummary: TenantSummary) => {
    setTenants((currentTenants) => currentTenants.map((tenant) => (tenant.id === nextSummary.id ? nextSummary : tenant)));
  };

  const handleCreateSubmit = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    setSubmitting(true);
    setErrorMessage(null);
    setNotice(null);

    try {
      const createdTenant = await createTenant(token, {
        code: normalizeFormValue(createForm.code),
        name: normalizeFormValue(createForm.name),
        contactName: normalizeOptionalValue(createForm.contactName),
        contactEmail: normalizeOptionalValue(createForm.contactEmail),
        notes: normalizeOptionalValue(createForm.notes),
        adminEmail: normalizeFormValue(createForm.adminEmail),
        adminDisplayName: normalizeFormValue(createForm.adminDisplayName),
        adminPassword: createForm.adminPassword
      });

      setCreateOpen(false);
      setCreateForm(emptyCreateForm());
      setNotice(`租户 ${createdTenant.name} 已创建。`);
      await refreshList(createdTenant.id);
    } catch (error) {
      setErrorMessage(error instanceof Error ? error.message : '租户创建失败。');
    } finally {
      setSubmitting(false);
    }
  };

  const handleEditSubmit = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    if (!selectedTenant) {
      return;
    }

    setSubmitting(true);
    setErrorMessage(null);
    setNotice(null);

    try {
      const updatedTenant = await updateTenant(token, selectedTenant.id, {
        name: normalizeFormValue(editForm.name),
        contactName: normalizeOptionalValue(editForm.contactName),
        contactEmail: normalizeOptionalValue(editForm.contactEmail),
        notes: normalizeOptionalValue(editForm.notes)
      });

      setSelectedTenant(updatedTenant);
      updateTenantSummary({
        id: updatedTenant.id,
        code: updatedTenant.code,
        name: updatedTenant.name,
        status: updatedTenant.status,
        contactName: updatedTenant.contactName,
        contactEmail: updatedTenant.contactEmail
      });
      setEditOpen(false);
      setNotice(`租户 ${updatedTenant.name} 已更新。`);
    } catch (error) {
      setErrorMessage(error instanceof Error ? error.message : '租户更新失败。');
    } finally {
      setSubmitting(false);
    }
  };

  const handleStatusChange = async (nextStatus: TenantSummary['status']) => {
    if (!selectedTenant) {
      return;
    }

    const confirmed =
      typeof window === 'undefined'
        ? true
        : window.confirm(
            nextStatus === 'DISABLED' ? '停用后该租户管理员将无法继续登录后台，是否继续？' : '确认重新启用该租户？'
          );

    if (!confirmed) {
      return;
    }

    setSubmitting(true);
    setErrorMessage(null);
    setNotice(null);

    try {
      const updatedSummary = await updateTenantStatus(token, selectedTenant.id, nextStatus);
      updateTenantSummary(updatedSummary);
      setSelectedTenant((currentTenant) =>
        currentTenant
          ? {
              ...currentTenant,
              status: updatedSummary.status,
              name: updatedSummary.name,
              contactName: updatedSummary.contactName,
              contactEmail: updatedSummary.contactEmail
            }
          : currentTenant
      );
      setNotice(nextStatus === 'DISABLED' ? `租户 ${updatedSummary.name} 已停用。` : `租户 ${updatedSummary.name} 已重新启用。`);
    } catch (error) {
      setErrorMessage(error instanceof Error ? error.message : '租户状态更新失败。');
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <>
      <SectionHeader
        title="租户管理"
        actions={
          <ActionButton onClick={() => {
              setCreateOpen(true);
              setEditOpen(false);
              setNotice(null);
            }}>
            新建租户
          </ActionButton>
        }
      />

      <PageStack>
        {errorMessage ? <NoticeBanner tone="error">{errorMessage}</NoticeBanner> : null}
        {notice ? <NoticeBanner tone="notice">{notice}</NoticeBanner> : null}
      </PageStack>

      <ListSection title="租户列表" description="列表中直接展示关键字段，详情通过点击租户名称打开，末列保留常用操作。">
        <ListTable
          rowKey="id"
          dataSource={tenants}
          loading={loadingList}
          emptyText="还没有租户，先创建第一个租户。"
          columns={[
            {
              key: 'name',
              title: '租户名称',
              render: (tenant) => (
                <TableLinkButton
                  onClick={() => {
                    setSelectedTenantId(tenant.id);
                    setDetailOpen(true);
                    setEditOpen(false);
                    setNotice(null);
                  }}
                >
                  {tenant.name}
                </TableLinkButton>
              )
            },
            {
              key: 'code',
              title: '编码',
              render: (tenant) => <span style={{ color: '#475569' }}>{tenant.code}</span>
            },
            {
              key: 'contactName',
              title: '联系人',
              render: (tenant) => tenant.contactName || '-'
            },
            {
              key: 'contactEmail',
              title: '邮箱',
              render: (tenant) => tenant.contactEmail || '-'
            },
            {
              key: 'status',
              title: '状态',
              render: (tenant) => <StatusTag color={tenant.status === 'ACTIVE' ? 'success' : 'default'}>{statusLabel(tenant.status)}</StatusTag>
            },
            {
              key: 'actions',
              title: '操作',
              width: 220,
              render: (tenant) => (
                <RowActionBar>
                  <RowActionButton
                    onClick={() => {
                      setSelectedTenantId(tenant.id);
                      setEditOpen(true);
                      setDetailOpen(false);
                    }}
                  >
                    编辑
                  </RowActionButton>
                  <RowActionButton
                    onClick={() => {
                      setSelectedTenantId(tenant.id);
                      setDetailOpen(false);
                      void handleStatusChange(tenant.status === 'ACTIVE' ? 'DISABLED' : 'ACTIVE');
                    }}
                    danger={tenant.status === 'ACTIVE'}
                    disabled={submitting}
                  >
                    {tenant.status === 'ACTIVE' ? '停用' : '启用'}
                  </RowActionButton>
                </RowActionBar>
              )
            }
          ]}
        />
      </ListSection>

      <DetailModal open={detailOpen && !!selectedTenant} title={selectedTenant ? `${selectedTenant.name} 详情` : '租户详情'} onCancel={() => setDetailOpen(false)} width={760}>
        {loadingDetail ? (
          <div style={{ color: '#64748b' }}>详情加载中...</div>
        ) : !selectedTenant ? (
          <div style={{ color: '#64748b' }}>从租户列表中选择一项后再查看详情。</div>
        ) : (
          <div style={{ display: 'grid', gap: 16 }}>
            <DetailSummaryHeader
              title={selectedTenant.name}
              subtitle={selectedTenant.code}
              status={<StatusTag color={selectedTenant.status === 'ACTIVE' ? 'success' : 'default'}>{statusLabel(selectedTenant.status)}</StatusTag>}
            />
            <div style={{ display: 'grid', gap: 10, color: '#334155' }}>
              <div>联系人：{selectedTenant.contactName || '未设置'}</div>
              <div>联系邮箱：{selectedTenant.contactEmail || '未设置'}</div>
              <div>备注：{selectedTenant.notes || '暂无备注'}</div>
              <div>初始管理员：{selectedTenant.admin ? `${selectedTenant.admin.displayName} · ${selectedTenant.admin.email}` : '暂无管理员信息'}</div>
              <div>用量摘要：当前版本先展示租户基础信息，用量统计将在套餐与额度模块联动接入。</div>
            </div>
            <ModalActionBar>
              <ActionButton onClick={() => {
                  setEditOpen(true);
                  setCreateOpen(false);
                  setDetailOpen(false);
                }} variant="outline" tone="neutral">
                编辑租户
              </ActionButton>
              <ActionButton disabled={submitting || selectedTenant.status === 'ACTIVE'} onClick={() => void handleStatusChange('ACTIVE')} tone="success">
                启用租户
              </ActionButton>
              <ActionButton disabled={submitting || selectedTenant.status === 'DISABLED'} onClick={() => void handleStatusChange('DISABLED')} tone="danger">
                停用租户
              </ActionButton>
            </ModalActionBar>
          </div>
        )}
      </DetailModal>

      <DetailModal
        open={createOpen}
        title="新建租户"
        onCancel={() => {
          setCreateOpen(false);
          setCreateForm(emptyCreateForm());
        }}
        width={760}
      >
        <TenantFormCard
          title="创建租户"
          description="创建租户时同步录入初始租户管理员账号。"
          submitLabel="创建租户"
          submitting={submitting}
          onSubmit={handleCreateSubmit}
          onCancel={() => {
            setCreateOpen(false);
            setCreateForm(emptyCreateForm());
          }}
        >
          <>
            <Field label="租户编码">
              <Input value={createForm.code} onChange={(event) => setCreateForm((current) => ({ ...current, code: event.target.value }))} required placeholder="tenant-acme" style={inputStyle()} />
            </Field>
            <Field label="租户名称">
              <Input value={createForm.name} onChange={(event) => setCreateForm((current) => ({ ...current, name: event.target.value }))} required placeholder="Acme Inc." style={inputStyle()} />
            </Field>
            <Field label="联系人">
              <Input value={createForm.contactName} onChange={(event) => setCreateForm((current) => ({ ...current, contactName: event.target.value }))} placeholder="Alice" style={inputStyle()} />
            </Field>
            <Field label="联系邮箱">
              <Input type="email" value={createForm.contactEmail} onChange={(event) => setCreateForm((current) => ({ ...current, contactEmail: event.target.value }))} placeholder="alice@acme.test" style={inputStyle()} />
            </Field>
            <Field label="备注">
              <TextArea value={createForm.notes} onChange={(event) => setCreateForm((current) => ({ ...current, notes: event.target.value }))} placeholder="记录租户背景、交付状态或风险备注。" autoSize={{ minRows: 5 }} style={inputStyle(true)} />
            </Field>
            <Field label="初始管理员邮箱">
              <Input type="email" value={createForm.adminEmail} onChange={(event) => setCreateForm((current) => ({ ...current, adminEmail: event.target.value }))} required placeholder="owner@acme.test" style={inputStyle()} />
            </Field>
            <Field label="初始管理员名称">
              <Input value={createForm.adminDisplayName} onChange={(event) => setCreateForm((current) => ({ ...current, adminDisplayName: event.target.value }))} required placeholder="Acme Owner" style={inputStyle()} />
            </Field>
            <Field label="初始管理员密码">
              <Password value={createForm.adminPassword} onChange={(event) => setCreateForm((current) => ({ ...current, adminPassword: event.target.value }))} required placeholder="Tenant123!" style={inputStyle()} />
            </Field>
          </>
        </TenantFormCard>
      </DetailModal>

      <DetailModal open={editOpen && !!selectedTenant} title="编辑租户" onCancel={() => setEditOpen(false)} width={760}>
        {selectedTenant ? (
          <TenantFormCard title="编辑租户" description="更新租户名称、联系人与备注信息。" submitLabel="保存修改" submitting={submitting} onSubmit={handleEditSubmit} onCancel={() => setEditOpen(false)}>
            <>
              <Field label="租户名称">
                <Input value={editForm.name} onChange={(event) => setEditForm((current) => ({ ...current, name: event.target.value }))} required style={inputStyle()} />
              </Field>
              <Field label="联系人">
                <Input value={editForm.contactName} onChange={(event) => setEditForm((current) => ({ ...current, contactName: event.target.value }))} style={inputStyle()} />
              </Field>
              <Field label="联系邮箱">
                <Input type="email" value={editForm.contactEmail} onChange={(event) => setEditForm((current) => ({ ...current, contactEmail: event.target.value }))} style={inputStyle()} />
              </Field>
              <Field label="备注">
                <TextArea value={editForm.notes} onChange={(event) => setEditForm((current) => ({ ...current, notes: event.target.value }))} autoSize={{ minRows: 5 }} style={inputStyle(true)} />
              </Field>
            </>
          </TenantFormCard>
        ) : null}
      </DetailModal>
    </>
  );
}
