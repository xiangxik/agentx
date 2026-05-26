import type { CSSProperties, PropsWithChildren, ReactNode } from 'react';
import {
  Alert,
  App as AntdApp,
  Button,
  Card,
  ConfigProvider,
  Flex,
  Layout,
  Menu,
  Modal,
  Space,
  Table,
  Tabs,
  Tag,
  Typography,
  theme
} from 'antd';
import { BarChartOutlined, DatabaseOutlined, FileSearchOutlined, SettingOutlined } from '@ant-design/icons';
import { useLocation, useNavigate } from 'react-router-dom';

import 'antd/dist/reset.css';

const { Header, Sider, Content } = Layout;
const { Title, Paragraph, Text } = Typography;

type NavItem = { to: string; label: string };

type ActionButtonTone = 'primary' | 'neutral' | 'success' | 'warning' | 'danger' | 'quiet';
type ActionButtonVariant = 'solid' | 'outline';

function actionButtonColors(tone: ActionButtonTone, variant: ActionButtonVariant) {
  const palette = {
    primary: { solidBg: '#1d4ed8', solidBorder: '#1d4ed8', solidText: '#ffffff', outlineBorder: '#cbd5e1', outlineText: '#1f2937', outlineBg: '#ffffff' },
    neutral: { solidBg: '#475569', solidBorder: '#475569', solidText: '#ffffff', outlineBorder: '#cbd5e1', outlineText: '#334155', outlineBg: '#ffffff' },
    success: { solidBg: '#166534', solidBorder: '#166534', solidText: '#ffffff', outlineBorder: '#86efac', outlineText: '#166534', outlineBg: '#ffffff' },
    warning: { solidBg: '#b45309', solidBorder: '#b45309', solidText: '#ffffff', outlineBorder: '#fbbf24', outlineText: '#92400e', outlineBg: '#ffffff' },
    danger: { solidBg: '#991b1b', solidBorder: '#991b1b', solidText: '#ffffff', outlineBorder: '#fecaca', outlineText: '#991b1b', outlineBg: '#ffffff' },
    quiet: { solidBg: '#0f172a', solidBorder: '#0f172a', solidText: '#ffffff', outlineBorder: '#334155', outlineText: '#e2e8f0', outlineBg: 'transparent' }
  }[tone];

  return variant === 'solid'
    ? { background: palette.solidBg, borderColor: palette.solidBorder, color: palette.solidText }
    : { background: palette.outlineBg, borderColor: palette.outlineBorder, color: palette.outlineText };
}

function navIcon(label: string) {
  if (label.includes('模型') || label.includes('知识')) {
    return <DatabaseOutlined />;
  }

  if (label.includes('审计') || label.includes('会话')) {
    return <FileSearchOutlined />;
  }

  if (label.includes('设置') || label.includes('套餐')) {
    return <SettingOutlined />;
  }

  return <BarChartOutlined />;
}

export function AdminShell({
  title,
  nav,
  actions,
  children
}: PropsWithChildren<{ title: string; nav: NavItem[]; actions?: ReactNode }>) {
  const location = useLocation();
  const navigate = useNavigate();
  const currentPath = nav.find((item) => location.pathname === item.to || location.pathname.startsWith(`${item.to}/`))?.to ?? nav[0]?.to;

  return (
    <ConfigProvider
      theme={{
        token: {
          colorPrimary: '#1677ff',
          borderRadius: 10,
          colorBgLayout: '#f5f7fb',
          colorText: '#172033',
          fontFamily: "'PingFang SC', 'Microsoft YaHei', 'Helvetica Neue', sans-serif"
        },
        components: {
          Layout: {
            bodyBg: '#f5f7fb',
            siderBg: '#0f172a',
            headerBg: '#ffffff'
          },
          Menu: {
            darkItemBg: '#0f172a',
            darkItemSelectedBg: '#1677ff',
            darkItemHoverBg: '#1d4ed8',
            darkItemColor: 'rgba(255,255,255,0.78)',
            darkItemSelectedColor: '#ffffff'
          },
          Card: {
            headerHeight: 56
          }
        }
      }}
    >
      <AntdApp>
        <Layout style={{ minHeight: '100vh' }}>
          <Sider width={248} breakpoint="lg" collapsedWidth={80} style={{ boxShadow: '12px 0 32px rgba(15, 23, 42, 0.16)' }}>
            <div style={{ padding: '24px 20px 12px' }}>
              <Text style={{ color: 'rgba(255,255,255,0.62)', fontSize: 12, letterSpacing: 1.2 }}>AGENTX ADMIN</Text>
              <Title level={3} style={{ color: '#fff', margin: '10px 0 0' }}>
                {title}
              </Title>
            </div>
            {actions ? <div style={{ padding: '0 16px 16px' }}>{actions}</div> : null}
            <Menu
              mode="inline"
              theme="dark"
              selectedKeys={currentPath ? [currentPath] : []}
              items={nav.map((item) => ({
                key: item.to,
                icon: navIcon(item.label),
                label: item.label
              }))}
              onClick={({ key }) => navigate(key)}
              style={{ borderInlineEnd: 0, background: 'transparent', paddingInline: 8 }}
            />
          </Sider>
          <Layout>
            <Header
              style={{
                padding: '0 28px',
                borderBottom: '1px solid rgba(15, 23, 42, 0.06)',
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'space-between'
              }}
            >
              <Space orientation="vertical" size={0}>
                <Text type="secondary">统一的 Ant Design Admin 风格后台</Text>
                <Text strong>{nav.find((item) => item.to === currentPath)?.label ?? title}</Text>
              </Space>
            </Header>
            <Content style={{ padding: 24 }}>
              <div style={{ maxWidth: 1600, margin: '0 auto', display: 'grid', gap: 20 }}>{children}</div>
            </Content>
          </Layout>
        </Layout>
      </AntdApp>
    </ConfigProvider>
  );
}

export function StatCard({ title, value, description }: { title: string; value: string; description: string }) {
  return (
    <Card styles={{ body: { padding: 20 } }}>
      <Text type="secondary">{title}</Text>
      <div style={{ fontSize: 28, fontWeight: 700, color: '#172033', marginTop: 10 }}>{value}</div>
      <Paragraph type="secondary" style={{ marginBottom: 0, marginTop: 12 }}>
        {description}
      </Paragraph>
    </Card>
  );
}

export function SectionHeader({ title, actions }: { title: string; actions?: ReactNode }) {
  return (
    <Flex align="center" justify="space-between" gap={16} wrap style={{ marginBottom: 4 }}>
      <div>
        <Title level={3} style={{ margin: 0 }}>
          {title}
        </Title>
      </div>
      {actions}
    </Flex>
  );
}

export function PageStack({ gap = 16, children }: PropsWithChildren<{ gap?: number }>) {
  return <div style={{ display: 'grid', gap }}>{children}</div>;
}

export function ActionToolbar({ children, justify = 'flex-start' }: PropsWithChildren<{ justify?: 'flex-start' | 'space-between' | 'flex-end' }>) {
  return (
    <Flex align="center" gap={12} wrap justify={justify}>
      {children}
    </Flex>
  );
}

export function SectionActionHeader({
  description,
  actions
}: {
  description?: ReactNode;
  actions?: ReactNode;
}) {
  return (
    <ActionToolbar justify="space-between">
      <div style={{ color: '#64748b' }}>{description}</div>
      {actions ?? null}
    </ActionToolbar>
  );
}

export function DetailSummaryHeader({
  title,
  subtitle,
  status
}: {
  title: ReactNode;
  subtitle?: ReactNode;
  status?: ReactNode;
}) {
  return (
    <div style={{ display: 'flex', justifyContent: 'space-between', gap: 12, alignItems: 'center' }}>
      <div>
        <h3 style={{ margin: 0 }}>{title}</h3>
        {subtitle ? <div style={{ color: '#64748b', marginTop: 4 }}>{subtitle}</div> : null}
      </div>
      {status ?? null}
    </div>
  );
}

export function FilterFieldsGrid({ columns = 'repeat(2, minmax(0, 280px))', children }: PropsWithChildren<{ columns?: string }>) {
  return <div style={{ display: 'grid', gridTemplateColumns: columns, gap: 16 }}>{children}</div>;
}

export function FilterSection({
  title = '筛选条件',
  description,
  columns,
  actions,
  children
}: PropsWithChildren<{
  title?: string;
  description?: string;
  columns?: string;
  actions?: ReactNode;
}>) {
  return (
    <SurfaceCard title={title} description={description}>
      <PageStack gap={16}>
        <FilterFieldsGrid columns={columns}>{children}</FilterFieldsGrid>
        {actions ?? null}
      </PageStack>
    </SurfaceCard>
  );
}

export function ListSection({
  title,
  description,
  actions,
  children
}: PropsWithChildren<{
  title: string;
  description?: string;
  actions?: ReactNode;
}>) {
  return (
    <SurfaceCard title={title} description={description}>
      <PageStack gap={16}>
        {actions ?? null}
        {children}
      </PageStack>
    </SurfaceCard>
  );
}

export function controlStyle(multiline = false): CSSProperties {
  return {
    width: '100%',
    boxSizing: 'border-box',
    borderRadius: 10,
    border: '1px solid #cbd5e1',
    padding: multiline ? '12px 14px' : '11px 14px',
    fontSize: 14,
    minHeight: multiline ? 110 : undefined,
    resize: multiline ? 'vertical' : undefined
  };
}

export function FormField({ label, children }: { label: string; children: ReactNode }) {
  return (
    <label style={{ display: 'grid', gap: 8 }}>
      <Text strong style={{ color: '#0f172a' }}>
        {label}
      </Text>
      {children}
    </label>
  );
}

export function NoticeBanner({ tone, children }: { tone: 'error' | 'notice'; children: ReactNode }) {
  return (
    <Alert
      type={tone === 'error' ? 'error' : 'info'}
      showIcon
      title={children}
      style={{ borderRadius: 10 }}
    />
  );
}

export function WorkspaceTabs({
  activeKey,
  onChange,
  items
}: {
  activeKey: string;
  onChange: (key: string) => void;
  items: Array<{ key: string; label: string; children: ReactNode }>;
}) {
  return (
    <Card styles={{ body: { paddingTop: 8 } }}>
      <Tabs activeKey={activeKey} onChange={onChange} items={items} />
    </Card>
  );
}

export function SurfaceCard({
  title,
  description,
  extra,
  children
}: PropsWithChildren<{ title: string; description?: string; extra?: ReactNode }>) {
  return (
    <Card
      title={title}
      extra={extra}
      styles={{ body: { display: 'grid', gap: 16 } }}
      style={{ borderRadius: 12 }}
    >
      {description ? (
        <Paragraph type="secondary" style={{ margin: 0 }}>
          {description}
        </Paragraph>
      ) : null}
      {children}
    </Card>
  );
}

export function FormSectionCard({
  title,
  description,
  submitLabel,
  submitting,
  onSubmit,
  onCancel,
  submitTone = 'primary',
  children
}: PropsWithChildren<{
  title: string;
  description: string;
  submitLabel: string;
  submitting: boolean;
  onSubmit: (event: React.FormEvent<HTMLFormElement>) => void;
  onCancel: () => void;
  submitTone?: ActionButtonTone;
}>) {
  return (
    <Card style={{ borderRadius: 12, boxShadow: '0 18px 50px rgba(15, 23, 42, 0.08)' }} styles={{ body: { display: 'grid', gap: 16, padding: 20 } }}>
      <div>
        <Title level={5} style={{ margin: 0 }}>
          {title}
        </Title>
        <Paragraph type="secondary" style={{ margin: '4px 0 0' }}>
          {description}
        </Paragraph>
      </div>
      <form onSubmit={onSubmit} style={{ display: 'grid', gap: 14 }}>
        {children}
        <Flex gap={12} justify="flex-end" wrap>
          <ActionButton onClick={onCancel} variant="outline" tone="neutral">
            取消
          </ActionButton>
          <ActionButton htmlType="submit" disabled={submitting} tone={submitTone}>
            {submitting ? '提交中...' : submitLabel}
          </ActionButton>
        </Flex>
      </form>
    </Card>
  );
}

export function DetailModal({
  open,
  title,
  onCancel,
  width,
  children
}: PropsWithChildren<{ open: boolean; title: string; onCancel: () => void; width?: number }>) {
  return (
    <Modal open={open} title={title} onCancel={onCancel} footer={null} width={width ?? 880} destroyOnHidden>
      {children}
    </Modal>
  );
}

export function StatusTag({ color, children }: PropsWithChildren<{ color?: string }>) {
  const { token } = theme.useToken();

  return (
    <Tag color={color ?? token.colorPrimary} style={{ borderRadius: 8, paddingInline: 10, fontWeight: 600 }}>
      {children}
    </Tag>
  );
}

export function ActionButton({
  children,
  onClick,
  disabled,
  htmlType = 'button',
  tone = 'primary',
  variant = 'solid',
  block = false
}: PropsWithChildren<{
  onClick?: () => void;
  disabled?: boolean;
  htmlType?: 'button' | 'submit' | 'reset';
  tone?: ActionButtonTone;
  variant?: ActionButtonVariant;
  block?: boolean;
}>) {
  const colors = actionButtonColors(tone, variant);

  return (
    <Button
      type="default"
      htmlType={htmlType}
      onClick={onClick}
      disabled={disabled}
      autoInsertSpace={false}
      style={{
        width: block ? '100%' : undefined,
        borderRadius: 10,
        borderWidth: 1,
        height: 38,
        paddingInline: 16,
        boxShadow: 'none',
        fontWeight: 600,
        ...colors
      }}
    >
      {children}
    </Button>
  );
}

export function ModalActionBar({ children }: PropsWithChildren) {
  return <Flex gap={10} wrap>{children}</Flex>;
}

export function SelectionCardButton({
  title,
  description,
  accentColor,
  selected,
  selectedLabel = '已选中',
  idleLabel = '可选',
  onClick
}: {
  title: string;
  description: string;
  accentColor: string;
  selected: boolean;
  selectedLabel?: string;
  idleLabel?: string;
  onClick: () => void;
}) {
  return (
    <Button
      type="default"
      onClick={onClick}
      autoInsertSpace={false}
      style={{
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'space-between',
        gap: 12,
        width: '100%',
        height: 'auto',
        minHeight: 68,
        textAlign: 'left',
        borderRadius: 12,
        border: selected ? `1px solid ${accentColor}` : '1px solid #e2e8f0',
        background: selected ? '#fff7ed' : '#ffffff',
        padding: '12px 14px',
        boxShadow: 'none'
      }}
    >
      <span style={{ display: 'flex', alignItems: 'center', gap: 12 }}>
        <span style={{ width: 14, height: 14, borderRadius: '50%', background: accentColor, flexShrink: 0 }} />
        <span>
          <strong style={{ display: 'block', textAlign: 'left' }}>{title}</strong>
          <span style={{ display: 'block', textAlign: 'left', color: '#64748b', fontSize: 13, whiteSpace: 'normal' }}>{description}</span>
        </span>
      </span>
      <span style={{ color: selected ? '#c2410c' : '#94a3b8', fontSize: 12, fontWeight: 700 }}>{selected ? selectedLabel : idleLabel}</span>
    </Button>
  );
}

type TableColumn<T> = {
  key: string;
  title: ReactNode;
  width?: number | string;
  render: (record: T) => ReactNode;
};

export function ListTable<T extends object>({
  rowKey,
  columns,
  dataSource,
  loading,
  emptyText,
  selectedRowKeys,
  onSelectionChange
}: {
  rowKey: keyof T | ((record: T) => string | number);
  columns: Array<TableColumn<T>>;
  dataSource: T[];
  loading?: boolean;
  emptyText?: ReactNode;
  selectedRowKeys?: Array<string | number>;
  onSelectionChange?: (keys: Array<string | number>) => void;
}) {
  return (
    <Table<T>
      rowKey={rowKey}
      columns={columns.map((column) => ({
        key: column.key,
        title: column.title,
        width: column.width,
        render: (_value: unknown, record: T) => column.render(record)
      }))}
      dataSource={dataSource}
      loading={loading}
      pagination={false}
      size="middle"
      rowSelection={
        onSelectionChange
          ? {
              selectedRowKeys,
              onChange: (keys) => onSelectionChange(keys as Array<string | number>)
            }
          : undefined
      }
      locale={{ emptyText: emptyText ?? '暂无数据' }}
      scroll={{ x: 'max-content' }}
    />
  );
}

export function RowActionBar({ children }: PropsWithChildren) {
  return <Space size={4} wrap>{children}</Space>;
}

export function RowActionButton({
  children,
  onClick,
  danger,
  disabled
}: PropsWithChildren<{ onClick: () => void; danger?: boolean; disabled?: boolean }>) {
  return (
    <Button type="link" danger={danger} disabled={disabled} onClick={onClick} style={{ paddingInline: 0, height: 'auto', borderRadius: 8 }}>
      {children}
    </Button>
  );
}

export function TableLinkButton({
  children,
  onClick,
  textAlign = 'left'
}: PropsWithChildren<{ onClick: () => void; textAlign?: 'left' | 'center' | 'right' }>) {
  return (
    <Button type="link" onClick={onClick} style={{ paddingInline: 0, height: 'auto', fontWeight: 600, textAlign, borderRadius: 8 }}>
      {children}
    </Button>
  );
}
