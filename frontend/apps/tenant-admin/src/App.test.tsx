import { render, screen } from '@testing-library/react';
import { describe, expect, it } from 'vitest';

import { App } from './App';

describe('tenant-admin app', () => {
  it('renders dashboard shell', () => {
    render(<App />);
    expect(screen.getByText('Tenant Admin')).toBeInTheDocument();
  });
});
