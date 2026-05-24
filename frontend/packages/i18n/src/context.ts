import { createContext } from 'react';
import type { Locale } from './locale';

export interface I18nContextValue {
  locale: Locale;
  setLocale: (locale: Locale) => void;
  t: (key: string) => string;
}

export const I18nContext = createContext<I18nContextValue | undefined>(undefined);
