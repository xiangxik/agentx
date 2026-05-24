import { useMemo, useState, type PropsWithChildren } from 'react';
import { I18nContext, type I18nContextValue } from './context';
import type { Locale } from './locale';
import { resources } from './locale';

function useI18nValue(defaultLocale: Locale) {
  const [locale, setLocale] = useState<Locale>(defaultLocale);

  return useMemo<I18nContextValue>(
    () => ({
      locale,
      setLocale,
      t: (key: string) => resources[locale][key] ?? key
    }),
    [locale]
  );
}

export function I18nProvider({ children, defaultLocale = 'zh-CN' }: PropsWithChildren<{ defaultLocale?: Locale }>) {
  return <I18nContext.Provider value={useI18nValue(defaultLocale)}>{children}</I18nContext.Provider>;
}
