import { useState, useEffect } from 'react';
import { Link } from 'react-router-dom';
import api from '../axios';
import { useAuth } from '../contexts/AuthContext';
import './HomePage.css';

export default function HomePage() {
  const { user, logout } = useAuth();
  const [knowledgeBases, setKnowledgeBases] = useState([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState(null);
  const [search, setSearch] = useState('');
  const [docTypeFilter, setDocTypeFilter] = useState('');
  const [page, setPage] = useState(1);
  const [pageSize, setPageSize] = useState(5);
  const [totalPages, setTotalPages] = useState(0);
  const [totalElements, setTotalElements] = useState(0);
  const [expandedRows, setExpandedRows] = useState([]);
  const [jumpPage, setJumpPage] = useState('');

  // Fetch KB documents from backend
  useEffect(() => {
    async function fetchDocuments() {
      setLoading(true);
      setError(null);
      try {
        const params = {
          page: page - 1,
          size: pageSize,
        };
        if (search.trim()) {
          params.keyword = search.trim();
        }
        if (docTypeFilter.trim()) {
          params.docType = docTypeFilter.trim();
        }

        const res = await api.get('/kb/documents/search', { params });
        const payload = res.data;

        // Backward compatibility with older backend response format
        if (Array.isArray(payload)) {
          setKnowledgeBases(payload);
          setTotalElements(payload.length);
          setTotalPages(Math.ceil(payload.length / pageSize));
        } else {
          setKnowledgeBases(payload.content || []);
          setTotalElements(payload.totalElements || 0);
          setTotalPages(payload.totalPages || 0);
        }
      } catch (err) {
        setError('Failed to fetch knowledge bases');
      } finally {
        setLoading(false);
      }
    }
    fetchDocuments();
  }, [page, pageSize, search, docTypeFilter]);

  useEffect(() => {
    if (totalPages > 0 && page > totalPages) {
      setPage(totalPages);
    }
  }, [page, totalPages]);

  function handleExpandRow(idx) {
    setExpandedRows((prev) =>
      prev.includes(idx) ? prev.filter(i => i !== idx) : [...prev, idx]
    );
  }

  // Delete KB document by id
  async function handleDelete(id) {
    if (!window.confirm('Delete this document?')) return;
    try {
      await api.delete(`/kb/documents/${id}`);
      setKnowledgeBases((prev) => prev.filter((kb) => kb.id !== id));
      setTotalElements((prev) => Math.max(0, prev - 1));
    } catch (err) {
      alert('Failed to delete document');
    }
  }

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
          <Link
            to="/telemetry"
            className="btn-logs"
            title="View Run Logs"
          >
            📊 Run Logs
          </Link>
          <Link
            to="/portfolio"
            className="btn-logs"
            title="View Portfolio"
          >
            🎨 Portfolio
          </Link>
          <Link
            to="/upload"
            className="btn-logs"
            title="Upload new knowledge"
          >
            📤 Upload
          </Link>
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
            <h3>Knowledge Base Documents</h3>
          </div>
          <div className="kb-search-bar">
            <input
              type="text"
              placeholder="Search content/type/metadata..."
              value={search}
              onChange={e => { setSearch(e.target.value); setPage(1); }}
              className="kb-search-input"
            />
            <input
              type="text"
              placeholder="Filter doc type (optional)..."
              value={docTypeFilter}
              onChange={e => { setDocTypeFilter(e.target.value); setPage(1); }}
              className="kb-search-input"
            />
          </div>
          {loading ? (
            <div className="empty-state"><p>Loading...</p></div>
          ) : error ? (
            <div className="empty-state"><p style={{color: 'red'}}>{error}</p></div>
          ) : knowledgeBases.length === 0 ? (
            <div className="empty-state">
              <p>{search.trim() || docTypeFilter.trim() ? 'No results found.' : 'No knowledge bases yet. Create one to get started.'}</p>
            </div>
          ) : (
            <div className="kb-table-wrapper small-table">
              <table className="kb-table">
                <thead>
                  <tr>
                    <th style={{width: '30%'}}>Type</th>
                    <th style={{width: '50%'}}>Content</th>
                    <th style={{width: '20%'}}>Actions</th>
                  </tr>
                </thead>
                <tbody>
                  {knowledgeBases.map((kb, idx) => (
                    <tr key={kb.id}>
                      <td>{kb.docType || kb.type || '-'}</td>
                      <td style={{maxWidth: 180, position: 'relative'}}>
                        <div
                          className={`kb-content-cell${expandedRows.includes(idx + (page-1)*pageSize) ? ' expanded' : ''}`}
                          style={{
                            maxHeight: expandedRows.includes(idx + (page-1)*pageSize) ? 'none' : '2.5em',
                            overflow: expandedRows.includes(idx + (page-1)*pageSize) ? 'visible' : 'hidden',
                            cursor: 'pointer',
                            whiteSpace: expandedRows.includes(idx + (page-1)*pageSize) ? 'pre-line' : 'nowrap',
                            textOverflow: expandedRows.includes(idx + (page-1)*pageSize) ? 'unset' : 'ellipsis',
                          }}
                          title={kb.content}
                          onClick={() => handleExpandRow(idx + (page-1)*pageSize)}
                        >
                          {kb.content || '-'}
                        </div>
                        {!expandedRows.includes(idx + (page-1)*pageSize) && kb.content && kb.content.length > 60 && (
                          <span className="kb-expand-hint">▼</span>
                        )}
                      </td>
                      <td>
                        <button className="btn-delete" onClick={() => handleDelete(kb.id)} title="Delete">🗑️</button>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
              <div className="kb-pagination">
                <button onClick={() => setPage(p => Math.max(1, p-1))} disabled={page === 1}>&lt; Prev</button>
                <span>Page {Math.min(page, Math.max(totalPages, 1))} of {Math.max(totalPages, 1)} ({totalElements} total)</span>
                <button
                  onClick={() => setPage(p => Math.min(Math.max(totalPages, 1), p + 1))}
                  disabled={totalPages <= 1 || page >= totalPages}
                >
                  Next &gt;
                </button>
                <span className="kb-jump-page">
                  跳转
                  <input
                    type="number"
                    min="1"
                    max={totalPages}
                    value={jumpPage}
                    placeholder={String(page)}
                    onChange={(e) => setJumpPage(e.target.value)}
                    onKeyDown={(e) => {
                      if (e.key === 'Enter') {
                        const val = parseInt(jumpPage, 10);
                        if (val >= 1 && val <= totalPages) {
                          setPage(val);
                          setJumpPage('');
                        }
                      }
                    }}
                  />
                  页
                  <button
                    onClick={() => {
                      const val = parseInt(jumpPage, 10);
                      if (val >= 1 && val <= totalPages) {
                        setPage(val);
                        setJumpPage('');
                      }
                    }}
                    disabled={!jumpPage || parseInt(jumpPage, 10) < 1 || parseInt(jumpPage, 10) > totalPages}
                  >
                    Go
                  </button>
                </span>
              </div>
            </div>
          )}
        </section>
      </main>
    </div>
  );
}
