import { createContext, useContext, useEffect, useState } from 'react';
import {
  onAuthStateChanged,
  signInWithPopup,
  signInWithEmailAndPassword,
  createUserWithEmailAndPassword,
  signOut,
} from 'firebase/auth';
import { auth, googleProvider, isFirebaseConfigured } from '../firebase';

const AuthContext = createContext(null);

export { AuthContext };

export function AuthProvider({ children }) {
  const [user, setUser] = useState(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    // If Firebase is not configured, bypass authentication
    if (!isFirebaseConfigured || !auth) {
      console.warn('Firebase not configured - bypassing authentication');
      // Create a mock user for demo purposes when Firebase is not configured
      setUser({ 
        uid: 'demo-user', 
        email: 'demo@example.com',
        displayName: 'Demo User',
        isDemo: true 
      });
      setLoading(false);
      return;
    }

    const unsubscribe = onAuthStateChanged(auth, (firebaseUser) => {
      setUser(firebaseUser);
      setLoading(false);
    });
    return unsubscribe;
  }, []);

  const loginWithGoogle = () => {
    if (!isFirebaseConfigured || !auth) {
      return Promise.reject(new Error('Firebase not configured'));
    }
    return signInWithPopup(auth, googleProvider);
  };

  const loginWithEmail = (email, password) => {
    if (!isFirebaseConfigured || !auth) {
      return Promise.reject(new Error('Firebase not configured'));
    }
    return signInWithEmailAndPassword(auth, email, password);
  };

  const registerWithEmail = (email, password) => {
    if (!isFirebaseConfigured || !auth) {
      return Promise.reject(new Error('Firebase not configured'));
    }
    return createUserWithEmailAndPassword(auth, email, password);
  };

  const logout = () => {
    if (!isFirebaseConfigured || !auth) {
      setUser(null);
      return Promise.resolve();
    }
    return signOut(auth);
  };

  const value = {
    user,
    loading,
    isFirebaseConfigured,
    loginWithGoogle,
    loginWithEmail,
    registerWithEmail,
    logout,
  };

  return (
    <AuthContext.Provider value={value}>
      {children}
    </AuthContext.Provider>
  );
}

export function useAuth() {
  const ctx = useContext(AuthContext);
  if (!ctx) throw new Error('useAuth must be used within an AuthProvider');
  return ctx;
}
