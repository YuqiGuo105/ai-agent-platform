import { useState, useEffect } from 'react';
import { useAuth } from '../contexts/AuthContext';
import './HomePage.css';

export default function HomePage() {
  const { user, logout } = useAuth();
  const [knowledgeBases, setKnowledgeBases] = useState([]);

  // Helper to get avatar URL with Google size param if needed
  function getAvatarUrl(url) {
    if (!url) return null;
    // Add ?sz=64 for Google profile images
    if (url.includes('googleusercontent.com') && !url.includes('sz=')) {
      return url + (url.includes('?') ? '&' : '?') + 'sz=64';
    }
    return url;
  }

  // Helper to get initials
  function getInitials(name, email) {
    if (name) {
      return name.split(' ').map((n) => n[0]).join('').toUpperCase();
    }
    if (email) {
      return email[0].toUpperCase();
    }
    return '?';
  }

  return (
    <div className="home-page">
      <nav className="topbar">
        <h1 className="topbar-title">Agent Knowledge</h1>
        <div className="topbar-right">
          <span className="user-label">
            {user?.displayName || user?.email || 'User'}
          </span>
          {user?.photoURL ? (
            <img
              src={getAvatarUrl(user.photoURL)}
              alt="avatar"
              className="user-avatar"
              onError={e => {
                e.target.onerror = null;
                e.target.style.display = 'none';
                e.target.nextSibling.style.display = 'flex';
              }}
            />
          ) : null}
          {/* Fallback initials avatar */}
          <span
            className="user-avatar-fallback"
            style={{
              display: user?.photoURL ? 'none' : 'flex',
              alignItems: 'center',
              justifyContent: 'center',
              width: 32,
              height: 32,
              borderRadius: '50%',
              background: '#dee2e6',
              color: '#495057',
              fontWeight: 600,
              fontSize: 14,
              marginLeft: 0,
            }}
          >
            {getInitials(user?.displayName, user?.email)}
          </span>
          <button className="btn-logout" onClick={logout}>
            Sign Out
          </button>
        </div>
      </nav>

      <main className="home-main">
        <section className="home-hero">
          <h2>Welcome back, {user?.displayName?.split(' ')[0] || 'there'}!</h2>
          <p>Manage your AI agent knowledge bases below.</p>
        </section>

        <section className="kb-section">
          <div className="kb-header">
            <h3>Knowledge Bases</h3>
          </div>

          {knowledgeBases.length === 0 ? (
            <div className="empty-state">
              <p>No knowledge bases yet. Create one to get started.</p>
            </div>
          ) : (
            <ul className="kb-list">
              {knowledgeBases.map((kb) => (
                <li key={kb.id} className="kb-card">
                  <span>{kb.name}</span>
                </li>
              ))}
            </ul>
          )}
        </section>
      </main>
    </div>
  );
}
