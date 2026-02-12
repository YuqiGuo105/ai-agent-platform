import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { MemoryRouter } from 'react-router-dom';
import { AuthContext } from '../contexts/AuthContext';
import LoginPage from '../pages/LoginPage';
import HomePage from '../pages/HomePage';
import * as axiosModule from '../axios';
import ProtectedRoute from '../components/ProtectedRoute';

const renderWithAuth = (ui, { user = null, loading = false, ...authOverrides } = {}, { route = '/' } = {}) => {
  const defaultAuth = {
    user,
    loading,
    loginWithGoogle: vi.fn(),
    loginWithEmail: vi.fn(),
    registerWithEmail: vi.fn(),
    logout: vi.fn(),
    ...authOverrides,
  };
  return render(
    <AuthContext.Provider value={defaultAuth}>
      <MemoryRouter initialEntries={[route]}>{ui}</MemoryRouter>
    </AuthContext.Provider>
  );
};

describe('LoginPage', () => {
  it('renders login form', () => {
    renderWithAuth(<LoginPage />);
    expect(screen.getByText('Agent Knowledge')).toBeInTheDocument();
    expect(screen.getByPlaceholderText('you@example.com')).toBeInTheDocument();
    expect(screen.getByPlaceholderText('••••••••')).toBeInTheDocument();
  });

  it('renders Google sign-in button', () => {
    renderWithAuth(<LoginPage />);
    expect(screen.getByText('Continue with Google')).toBeInTheDocument();
  });

  it('toggles between sign-in and register mode', () => {
    renderWithAuth(<LoginPage />);
    expect(screen.getByRole('button', { name: /sign in$/i })).toBeInTheDocument();
    fireEvent.click(screen.getByText(/Register/));
    expect(screen.getByRole('button', { name: /create account/i })).toBeInTheDocument();
  });
});

describe('HomePage', () => {
  const mockUser = { displayName: 'Test User', email: 'test@example.com', photoURL: null };

  it('renders welcome message with user name', () => {
    renderWithAuth(<HomePage />, { user: mockUser });
    expect(screen.getByText(/Test User/)).toBeInTheDocument();
  });

  it('shows empty state for knowledge bases', async () => {
    vi.spyOn(axiosModule.default, 'get').mockResolvedValueOnce({ data: [] });
    renderWithAuth(<HomePage />, { user: mockUser });
    expect(await screen.findByText(/No knowledge bases yet/)).toBeInTheDocument();
  });

  it('renders sign out button', () => {
    renderWithAuth(<HomePage />, { user: mockUser });
    expect(screen.getByText('Sign Out')).toBeInTheDocument();
  });
});

describe('ProtectedRoute', () => {
  it('shows loading state', () => {
    renderWithAuth(
      <ProtectedRoute><div>Protected</div></ProtectedRoute>,
      { loading: true }
    );
    expect(screen.getByText('Loading…')).toBeInTheDocument();
  });

  it('renders children when user is authenticated', () => {
    renderWithAuth(
      <ProtectedRoute><div>Protected Content</div></ProtectedRoute>,
      { user: { email: 'a@b.com' } }
    );
    expect(screen.getByText('Protected Content')).toBeInTheDocument();
  });
});
