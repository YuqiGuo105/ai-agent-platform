import { useState, useEffect, useCallback } from 'react';
import { Link } from 'react-router-dom';
import { getDlqMessages, getDlqStats, requeueDlqMessage, ignoreDlqMessage, batchRequeueDlqMessages } from '../../services/telemetryApi';
import { formatDateTime, formatJson } from '../../utils/formatters';
import './TelemetryStyles.css';

/**
 * DLQ Monitor - Dead Letter Queue monitoring and management
 * Features:
 * - DLQ statistics overview
 * - Message list with status filters
 * - Requeue and ignore actions
 * - Batch requeue capability
 * - Message detail modal
 */
function DlqMonitor() {
  // State
  const [messages, setMessages] = useState([]);
  const [stats, setStats] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);
  const [selectedStatus, setSelectedStatus] = useState('');
  const [page, setPage] = useState(0);
  const [totalPages, setTotalPages] = useState(0);
  const [selectedIds, setSelectedIds] = useState([]);
  const [selectedMessage, setSelectedMessage] = useState(null);
  const [actionLoading, setActionLoading] = useState(false);
  const [jumpPage, setJumpPage] = useState('');

  // Fetch stats
  const fetchStats = useCallback(async () => {
    try {
      const data = await getDlqStats();
      setStats(data);
    } catch (err) {
      console.error('Failed to fetch DLQ stats:', err);
    }
  }, []);

  // Fetch messages
  const fetchMessages = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const params = { page, size: 20 };
      if (selectedStatus) params.status = selectedStatus;
      
      const data = await getDlqMessages(params);
      setMessages(data.content || []);
      setTotalPages(data.totalPages || 0);
    } catch (err) {
      setError(err.message || 'Failed to fetch DLQ messages');
      setMessages([]);
    } finally {
      setLoading(false);
    }
  }, [selectedStatus, page]);

  useEffect(() => {
    fetchStats();
    fetchMessages();
  }, [fetchStats, fetchMessages]);

  // Refresh data
  const handleRefresh = () => {
    setSelectedIds([]);
    fetchStats();
    fetchMessages();
  };

  // Handle requeue single message
  const handleRequeue = async (id) => {
    setActionLoading(true);
    try {
      await requeueDlqMessage(id);
      handleRefresh();
    } catch (err) {
      setError(`Failed to requeue message: ${err.message}`);
    } finally {
      setActionLoading(false);
    }
  };

  // Handle ignore single message
  const handleIgnore = async (id) => {
    setActionLoading(true);
    try {
      await ignoreDlqMessage(id);
      handleRefresh();
    } catch (err) {
      setError(`Failed to ignore message: ${err.message}`);
    } finally {
      setActionLoading(false);
    }
  };

  // Handle batch requeue
  const handleBatchRequeue = async () => {
    if (selectedIds.length === 0) return;
    setActionLoading(true);
    try {
      const result = await batchRequeueDlqMessages(selectedIds);
      if (result.failedIds && result.failedIds.length > 0) {
        setError(`Requeued ${result.successCount} messages, failed: ${result.failedIds.join(', ')}`);
      }
      handleRefresh();
    } catch (err) {
      setError(`Batch requeue failed: ${err.message}`);
    } finally {
      setActionLoading(false);
    }
  };

  // Toggle selection
  const toggleSelection = (id) => {
    setSelectedIds((prev) =>
      prev.includes(id) ? prev.filter((x) => x !== id) : [...prev, id]
    );
  };

  // Toggle all selection
  const toggleAllSelection = () => {
    if (selectedIds.length === messages.length) {
      setSelectedIds([]);
    } else {
      setSelectedIds(messages.map((m) => m.id));
    }
  };

  // Get status badge class
  const getStatusClass = (status) => {
    return status?.toLowerCase() || 'unknown';
  };

  return (
    <div className="telemetry-page">
      <div className="telemetry-header">
        <div>
          <Link to="/" className="back-link">← Back to Knowledge Base</Link>
          <h1>📬 DLQ Monitor</h1>
        </div>
        <button className="btn btn-secondary" onClick={handleRefresh} disabled={actionLoading}>
          🔄 Refresh
        </button>
      </div>

      {/* Stats Cards */}
      <div className="stats-grid">
        <div className="stat-card">
          <div className="stat-value">{stats?.totalCount || 0}</div>
          <div className="stat-label">Total Messages</div>
        </div>
        <div className="stat-card">
          <div className="stat-value" style={{ color: '#f97316' }}>
            {stats?.pendingCount || 0}
          </div>
          <div className="stat-label">Pending</div>
        </div>
        <div className="stat-card">
          <div className="stat-value" style={{ color: '#3b82f6' }}>
            {stats?.requeuedCount || 0}
          </div>
          <div className="stat-label">Requeued</div>
        </div>
        <div className="stat-card">
          <div className="stat-value" style={{ color: '#6b7280' }}>
            {stats?.ignoredCount || 0}
          </div>
          <div className="stat-label">Ignored</div>
        </div>
      </div>

      {/* Error display */}
      {error && (
        <div className="error-box">
          ⚠️ {error}
          <button
            onClick={() => setError(null)}
            style={{ marginLeft: '10px', background: 'none', border: 'none', cursor: 'pointer' }}
          >
            ✕
          </button>
        </div>
      )}

      {/* Filters and Batch Actions */}
      <div className="card">
        <div className="filter-bar">
          <div className="filter-group">
            <label>Status</label>
            <select
              value={selectedStatus}
              onChange={(e) => {
                setSelectedStatus(e.target.value);
                setPage(0);
              }}
            >
              <option value="">All Statuses</option>
              <option value="PENDING">Pending</option>
              <option value="REQUEUED">Requeued</option>
              <option value="IGNORED">Ignored</option>
            </select>
          </div>
          {selectedIds.length > 0 && (
            <button
              className="btn btn-primary"
              onClick={handleBatchRequeue}
              disabled={actionLoading}
            >
              🔁 Requeue Selected ({selectedIds.length})
            </button>
          )}
        </div>
      </div>

      {/* Messages Table */}
      <div className="card">
        <div className="card-header">
          <span className="card-title">DLQ Messages</span>
        </div>

        {loading ? (
          <div className="loading">
            <div className="spinner"></div>
          </div>
        ) : messages.length === 0 ? (
          <div className="empty-state">
            <div className="empty-state-icon">✅</div>
            <p>No DLQ messages found</p>
          </div>
        ) : (
          <>
            <div className="table-container">
              <table>
                <thead>
                  <tr>
                    <th>
                      <input
                        type="checkbox"
                        checked={selectedIds.length === messages.length && messages.length > 0}
                        onChange={toggleAllSelection}
                      />
                    </th>
                    <th>ID</th>
                    <th>Queue</th>
                    <th>Status</th>
                    <th>Error</th>
                    <th>Retry Count</th>
                    <th>Created At</th>
                    <th>Actions</th>
                  </tr>
                </thead>
                <tbody>
                  {messages.map((msg) => (
                    <tr key={msg.id}>
                      <td>
                        <input
                          type="checkbox"
                          checked={selectedIds.includes(msg.id)}
                          onChange={() => toggleSelection(msg.id)}
                        />
                      </td>
                      <td className="mono">{msg.id}</td>
                      <td className="mono truncate" title={msg.originalQueue}>
                        {msg.originalQueue || '-'}
                      </td>
                      <td>
                        <span className={`status-badge ${getStatusClass(msg.status)}`}>
                          {msg.status}
                        </span>
                      </td>
                      <td className="truncate" title={msg.errorMessage}>
                        {msg.errorMessage || '-'}
                      </td>
                      <td>{msg.retryCount || 0}</td>
                      <td>{formatDateTime(msg.createdAt)}</td>
                      <td>
                        <div style={{ display: 'flex', gap: '8px' }}>
                          <button
                            className="btn btn-sm btn-secondary"
                            onClick={() => setSelectedMessage(msg)}
                          >
                            👁️
                          </button>
                          {msg.status === 'PENDING' && (
                            <>
                              <button
                                className="btn btn-sm btn-primary"
                                onClick={() => handleRequeue(msg.id)}
                                disabled={actionLoading}
                              >
                                🔁
                              </button>
                              <button
                                className="btn btn-sm btn-secondary"
                                onClick={() => handleIgnore(msg.id)}
                                disabled={actionLoading}
                              >
                                🚫
                              </button>
                            </>
                          )}
                        </div>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>

            {/* Pagination */}
            {totalPages > 1 && (
              <div className="pagination">
                <button
                  disabled={page === 0}
                  onClick={() => setPage(page - 1)}
                >
                  ← Previous
                </button>
                <span style={{ padding: '8px 14px' }}>
                  Page {page + 1} of {totalPages}
                </span>
                <button
                  disabled={page >= totalPages - 1}
                  onClick={() => setPage(page + 1)}
                >
                  Next →
                </button>
                <span className="jump-page">
                  跳转
                  <input
                    type="number"
                    min="1"
                    max={totalPages}
                    value={jumpPage}
                    placeholder={String(page + 1)}
                    onChange={(e) => setJumpPage(e.target.value)}
                    onKeyDown={(e) => {
                      if (e.key === 'Enter') {
                        const val = parseInt(jumpPage, 10);
                        if (val >= 1 && val <= totalPages) {
                          setPage(val - 1);
                          setJumpPage('');
                        }
                      }
                    }}
                  />
                  页
                  <button
                    className="btn btn-sm btn-primary"
                    onClick={() => {
                      const val = parseInt(jumpPage, 10);
                      if (val >= 1 && val <= totalPages) {
                        setPage(val - 1);
                        setJumpPage('');
                      }
                    }}
                    disabled={!jumpPage || parseInt(jumpPage, 10) < 1 || parseInt(jumpPage, 10) > totalPages}
                  >
                    Go
                  </button>
                </span>
              </div>
            )}
          </>
        )}
      </div>

      {/* Message Detail Modal */}
      {selectedMessage && (
        <div className="modal-overlay" onClick={() => setSelectedMessage(null)}>
          <div className="modal" onClick={(e) => e.stopPropagation()}>
            <div className="modal-header">
              <span className="modal-title">Message Detail - ID: {selectedMessage.id}</span>
              <button className="modal-close" onClick={() => setSelectedMessage(null)}>
                ×
              </button>
            </div>
            <div className="modal-body">
              <div className="metadata-grid" style={{ marginBottom: '20px' }}>
                <div className="metadata-item">
                  <span className="metadata-label">Status</span>
                  <span className={`status-badge ${getStatusClass(selectedMessage.status)}`}>
                    {selectedMessage.status}
                  </span>
                </div>
                <div className="metadata-item">
                  <span className="metadata-label">Queue</span>
                  <span className="metadata-value mono">{selectedMessage.originalQueue}</span>
                </div>
                <div className="metadata-item">
                  <span className="metadata-label">Routing Key</span>
                  <span className="metadata-value mono">{selectedMessage.routingKey || '-'}</span>
                </div>
                <div className="metadata-item">
                  <span className="metadata-label">Retry Count</span>
                  <span className="metadata-value">{selectedMessage.retryCount || 0}</span>
                </div>
                <div className="metadata-item">
                  <span className="metadata-label">Created At</span>
                  <span className="metadata-value">{formatDateTime(selectedMessage.createdAt)}</span>
                </div>
                <div className="metadata-item">
                  <span className="metadata-label">Updated At</span>
                  <span className="metadata-value">{formatDateTime(selectedMessage.updatedAt)}</span>
                </div>
              </div>
              
              {selectedMessage.errorMessage && (
                <div style={{ marginBottom: '20px' }}>
                  <strong style={{ fontSize: '0.85rem', color: '#6b7280' }}>Error Message:</strong>
                  <div className="error-box" style={{ marginTop: '8px' }}>
                    {selectedMessage.errorMessage}
                  </div>
                </div>
              )}

              <div>
                <strong style={{ fontSize: '0.85rem', color: '#6b7280' }}>Message Body:</strong>
                <div className="expanded-content" style={{ marginTop: '8px' }}>
                  {formatJson(selectedMessage.messageBody)}
                </div>
              </div>
            </div>
            <div className="modal-footer">
              {selectedMessage.status === 'PENDING' && (
                <>
                  <button
                    className="btn btn-primary"
                    onClick={() => {
                      handleRequeue(selectedMessage.id);
                      setSelectedMessage(null);
                    }}
                    disabled={actionLoading}
                  >
                    🔁 Requeue
                  </button>
                  <button
                    className="btn btn-secondary"
                    onClick={() => {
                      handleIgnore(selectedMessage.id);
                      setSelectedMessage(null);
                    }}
                    disabled={actionLoading}
                  >
                    🚫 Ignore
                  </button>
                </>
              )}
              <button className="btn btn-secondary" onClick={() => setSelectedMessage(null)}>
                Close
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}

export default DlqMonitor;
