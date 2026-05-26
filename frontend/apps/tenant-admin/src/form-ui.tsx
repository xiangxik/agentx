import type { FormEvent, ReactNode } from 'react';

import { FormField, FormSectionCard, NoticeBanner, controlStyle } from '@agentx/admin-ui';
import type { CreateChatbotRequest, CreateFaqRequest } from '@agentx/api-client';

export type ChatbotFormState = CreateChatbotRequest;
export type FaqFormState = CreateFaqRequest;

export const CHAT_STYLE_OPTIONS = [
  { value: 'executive', label: 'Executive Horizon', description: '标准商务蓝，适合通用企业客服。', color: '#2563eb' },
  { value: 'slate', label: 'Slate Ledger', description: '冷静石板灰，适合金融与管理后台。', color: '#475569' },
  { value: 'heritage', label: 'Heritage Reserve', description: '沉稳酒红与米色，适合高端服务品牌。', color: '#8b1e3f' },
  { value: 'forest', label: 'Forest Council', description: '低饱和深绿，适合法务、医疗与咨询。', color: '#166534' },
  { value: 'graphite', label: 'Graphite Boardroom', description: '黑灰现代风，适合科技与企业门户。', color: '#0f172a' }
] as const;

export function inputStyle(multiline = false) {
  return controlStyle(multiline);
}

export function emptyChatbotForm(tenantId: number): ChatbotFormState {
  return {
    tenantId,
    name: '',
    description: '',
    language: 'zh-CN',
    status: 'DRAFT'
  };
}

export function emptyFaqForm(tenantId: number, chatbotId: number): FaqFormState {
  return {
    tenantId,
    chatbotId,
    language: 'zh-CN',
    question: '',
    alternateQuestions: [],
    answer: ''
  };
}

export function normalizeLines(value: string) {
  return value
    .split('\n')
    .map((item) => item.trim())
    .filter(Boolean);
}

export function Field({ label, children }: { label: string; children: ReactNode }) {
  return <FormField label={label}>{children}</FormField>;
}

export function Banner({ tone, children }: { tone: 'error' | 'notice'; children: string }) {
  return <NoticeBanner tone={tone}>{children}</NoticeBanner>;
}

export function FormCard({
  title,
  description,
  submitLabel,
  submitting,
  onSubmit,
  onCancel,
  children
}: {
  title: string;
  description: string;
  submitLabel: string;
  submitting: boolean;
  onSubmit: (event: FormEvent<HTMLFormElement>) => void;
  onCancel: () => void;
  children: ReactNode;
}) {
  return (
    <FormSectionCard title={title} description={description} submitLabel={submitLabel} submitting={submitting} onSubmit={onSubmit} onCancel={onCancel} submitTone="warning">
      {children}
    </FormSectionCard>
  );
}
