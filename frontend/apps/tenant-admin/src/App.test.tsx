import { render, screen } from '@testing-library/react';
import { describe, expect, it } from 'vitest';

import { App } from './App';

describe('tenant-admin app', () => {
  it('renders login page by default', () => {
    render(<App />);
    expect(screen.getByText('登录租户管理台')).toBeInTheDocument();
  });
});
