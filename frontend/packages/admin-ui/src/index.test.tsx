import { render, screen } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { describe, expect, it } from 'vitest';

import { AdminShell } from './index';

describe('admin-ui', () => {
  it('renders navigation labels', () => {
    render(
      <MemoryRouter>
        <AdminShell title="Test" nav={[{ to: '/', label: 'Overview' }]}>content</AdminShell>
      </MemoryRouter>
    );

    expect(screen.getByText('Overview')).toBeInTheDocument();
  });
});
