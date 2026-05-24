import { render, screen } from '@testing-library/react';
import { describe, expect, it } from 'vitest';

import { App } from './App';

describe('super-admin app', () => {
  it('renders shell title', () => {
    render(<App />);
    expect(screen.getByText('Super Admin')).toBeInTheDocument();
  });
});
