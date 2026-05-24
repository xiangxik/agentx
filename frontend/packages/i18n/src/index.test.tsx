import { renderHook } from '@testing-library/react';
import { describe, expect, it } from 'vitest';

import { I18nProvider, useI18n } from './index';

describe('i18n package', () => {
  it('returns localized labels', () => {
    const { result } = renderHook(() => useI18n(), {
      wrapper: ({ children }) => <I18nProvider>{children}</I18nProvider>
    });

    expect(result.current.t('dashboard')).toBe('工作台');
  });
});
