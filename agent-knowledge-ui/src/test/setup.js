import '@testing-library/jest-dom';
import { vi } from 'vitest';

// Mock Firebase so it never initialises during tests
vi.mock('../firebase', () => ({
  auth: {},
  googleProvider: {},
  app: {},
  analytics: null,
}));

vi.mock('firebase/auth', () => ({
  onAuthStateChanged: vi.fn(() => vi.fn()),
  signInWithPopup: vi.fn(),
  signInWithEmailAndPassword: vi.fn(),
  createUserWithEmailAndPassword: vi.fn(),
  signOut: vi.fn(),
  getAuth: vi.fn(),
  GoogleAuthProvider: vi.fn(),
}));
