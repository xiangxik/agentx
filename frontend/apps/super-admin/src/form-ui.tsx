import type { FormEvent, ReactElement } from 'react';

import { FormField, FormSectionCard, controlStyle } from '@agentx/admin-ui';

export function TenantFormCard({
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
  children: ReactElement | ReactElement[];
}) {
  return (
    <FormSectionCard title={title} description={description} submitLabel={submitLabel} submitting={submitting} onSubmit={onSubmit} onCancel={onCancel}>
      {children}
    </FormSectionCard>
  );
}

export function Field({ label, children }: { label: string; children: ReactElement }) {
  return <FormField label={label}>{children}</FormField>;
}

export function inputStyle(multiline = false) {
  return controlStyle(multiline);
}
