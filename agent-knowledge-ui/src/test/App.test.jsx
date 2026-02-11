import { render, screen } from '@testing-library/react';
import { describe, it, expect } from 'vitest';
import App from '../App';

describe('App', () => {
  it('renders the heading', () => {
    render(<App />);
    expect(screen.getByText('Agent Knowledge Base')).toBeInTheDocument();
  });

  it('shows empty state when no knowledge bases exist', () => {
    render(<App />);
    expect(
      screen.getByText(/No knowledge bases yet/)
    ).toBeInTheDocument();
  });
});
