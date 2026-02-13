import { useState, useEffect, useCallback } from 'react';
import { useNavigate, useSearchParams, Link } from 'react-router-dom';
import { searchRuns } from '../../services/telemetryApi';
import { formatDateTime, formatDuration } from '../../utils/formatters';
import './TelemetryStyles.css';

/**
 * Run Logs Dashboard - Main page for viewing and searching knowledge runs
 * Features:
 * - Search by session ID
 * - Filter by status
 * - Paginated results with collapsible rows
 * - Click to expand and view run details
 */
function RunLogsDashboard() {
  const navigate = useNavigate();
  const [searchParams, setSearchParams] = useSearchParams();
  
  // State
  const [runs, setRuns] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);
  const [totalPages, setTotalPages] = useState(0);
  const [totalElements, setTotalElements] = useState(0);
  const [jumpPage, setJumpPage] = useState('');
  const [expandedRows, setExpandedRows] = useState(new Set());
  
  // Filters from URL params
  const sessionId = searchParams.get('sessionId') || '';
  const status = searchParams.get('status') || '';
  const page = parseInt(searchParams.get('page') || '0', 10);
  const size = 20;

  // Fetch runs
  const fetchRuns = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const params = { page, size };
      if (sessionId) params.sessionId = sessionId;
      if (status) params.status = status;
      
      const data = await searchRuns(params);
      
      // Handle both array response and paginated object response
      if (Array.isArray(data)) {
        // API returns plain array - do client-side pagination
        const startIdx = page * size;
        const endIdx = startIdx + size;
        setRuns(data.slice(startIdx, endIdx));
        setTotalPages(Math.ceil(data.length / size));
        setTotalElements(data.length);
      } else {
        // API returns paginated object
        setRuns(data.content || []);
        setTotalPages(data.totalPages || 0);
        setTotalElements(data.totalElements || 0);
      }
    } catch (err) {
      setError(err.message || 'Failed to fetch runs');
      setRuns([]);
    } finally {
      setLoading(false);
    }
  }, [sessionId, status, page, size]);

  useEffect(() => {
    fetchRuns();
  }, [fetchRuns]);

  // Update URL params
  const updateFilter = (key, value) => {
    const newParams = new URLSearchParams(searchParams);
    if (value) {
      newParams.set(key, value);
    } else {
      newParams.delete(key);
    }
    // Reset to first page when filter changes
    if (key !== 'page') {
      newParams.delete('page');
    }
    setSearchParams(newParams);
  };

  // Navigate to detail page
  const handleRowClick = (runId) => {
    navigate(`/telemetry/runs/${runId}`);
  };

  // Get status badge class
  const getStatusClass = (status) => {
    return status?.toLowerCase() || 'unknown';
  };

  // Toggle row expansion
  const toggleRow = (runId) => {
    setExpandedRows(prev => {
      const newSet = new Set(prev);
      if (newSet.has(runId)) {
        newSet.delete(runId);
      } else {
        newSet.add(runId);
      }
      return newSet;
    });
  };

  // Get status icon
  const getStatusIcon = (status) => {
    switch (status) {
      case 'DONE': return '✅';
      case 'RUNNING': return '🔄';
      case 'FAILED': return '❌';
      case 'CANCELLED': return '⏹️';
      default: return '❓';
    }
  };

  return (
    <div className="telemetry-page">
      <div className="telemetry-header">
        <div>
          <Link to="/" className="back-link">← Back to Knowledge Base</Link>
          <h1>📊 Run Logs Dashboard</h1>
        </div>
        <button className="btn btn-secondary" onClick={fetchRuns}>
          🔄 Refresh
        </button>
      </div>

      {/* Filters */}
      <div className="card">
        <div className="filter-bar">
          <div className="filter-group">
            <label>Session ID</label>
            <input
              type="text"
              placeholder="Search by session ID..."
              value={sessionId}
              onChange={(e) => updateFilter('sessionId', e.target.value)}
            />
          </div>
          <div className="filter-group">
            <label>Status</label>
            <select
              value={status}
              onChange={(e) => updateFilter('status', e.target.value)}
            >
              <option value="">All Statuses</option>
              <option value="RUNNING">Running</option>
              <option value="DONE">Done</option>
              <option value="FAILED">Failed</option>
              <option value="CANCELLED">Cancelled</option>
            </select>
          </div>
        </div>
      </div>

      {/* Error display */}
      {error && (
        <div className="error-box">
          ⚠️ {error}
        </div>
      )}

      {/* Results */}
      <div className="card">
        <div className="card-header">
          <span className="card-title">
            Knowledge Runs {totalElements > 0 && `(${totalElements} total)`}
          </span>
        </div>

        {loading ? (
          <div className="loading">
            <div className="spinner"></div>
          </div>
        ) : runs.length === 0 ? (
          <div className="empty-state">
            <div className="empty-state-icon">📭</div>
            <p>No runs found matching your criteria</p>
          </div>
        ) : (
          <>
            <div className="table-container">
              <table className="runs-table">
                <thead>
                  <tr>
                    <th style={{ width: '40px' }}></th>
                    <th>Status</th>
                    <th>Question</th>
                    <th>Created At</th>
                    <th>Latency</th>
                    <th>Mode</th>
                  </tr>
                </thead>
                <tbody>
                  {runs.map((run) => (
                    <>
                      <tr
                        key={run.id}
                        className={`clickable-row ${expandedRows.has(run.id) ? 'expanded' : ''}`}
                        onClick={() => toggleRow(run.id)}
                      >
                        <td className="expand-cell">
                          <span className="expand-icon">{expandedRows.has(run.id) ? '▼' : '▶'}</span>
                        </td>
                        <td>
                          <span className={`status-badge ${getStatusClass(run.status)}`}>
                            {getStatusIcon(run.status)} {run.status}
                          </span>
                        </td>
                        <td className="question-cell" title={run.question}>
                          {run.question || <span className="text-muted">No question</span>}
                        </td>
                        <td className="nowrap">{formatDateTime(run.createdAt)}</td>
                        <td className="nowrap">
                          {run.totalLatencyMs
                            ? `${(run.totalLatencyMs / 1000).toFixed(2)}s`
                            : run.status === 'RUNNING'
                            ? '⏳ Running...'
                            : '-'}
                        </td>
                        <td>
                          <span className={`mode-badge ${run.mode?.toLowerCase() || 'general'}`}>
                            {run.mode || 'GENERAL'}
                          </span>
                        </td>
                      </tr>
                      {expandedRows.has(run.id) && (
                        <tr key={`${run.id}-details`} className="details-row">
                          <td colSpan="6">
                            <div className="run-details">
                              <div className="details-grid">
                                <div className="detail-section">
                                  <h4>Identifiers</h4>
                                  <div className="detail-item">
                                    <span className="detail-label">Run ID:</span>
                                    <code className="detail-value">{run.id}</code>
                                  </div>
                                  <div className="detail-item">
                                    <span className="detail-label">Session ID:</span>
                                    <code className="detail-value">{run.sessionId || '-'}</code>
                                  </div>
                                  <div className="detail-item">
                                    <span className="detail-label">Trace ID:</span>
                                    <code className="detail-value">{run.traceId || '-'}</code>
                                  </div>
                                  <div className="detail-item">
                                    <span className="detail-label">User ID:</span>
                                    <code className="detail-value">{run.userId || '-'}</code>
                                  </div>
                                </div>
                                <div className="detail-section">
                                  <h4>Timing</h4>
                                  <div className="detail-item">
                                    <span className="detail-label">Created:</span>
                                    <span className="detail-value">{formatDateTime(run.createdAt)}</span>
                                  </div>
                                  <div className="detail-item">
                                    <span className="detail-label">Updated:</span>
                                    <span className="detail-value">{formatDateTime(run.updatedAt)}</span>
                                  </div>
                                  <div className="detail-item">
                                    <span className="detail-label">Total Latency:</span>
                                    <span className="detail-value">
                                      {run.totalLatencyMs ? `${run.totalLatencyMs}ms (${(run.totalLatencyMs / 1000).toFixed(2)}s)` : '-'}
                                    </span>
                                  </div>
                                  <div className="detail-item">
                                    <span className="detail-label">KB Latency:</span>
                                    <span className="detail-value">{run.kbLatencyMs ? `${run.kbLatencyMs}ms` : '-'}</span>
                                  </div>
                                </div>
                                <div className="detail-section">
                                  <h4>Configuration</h4>
                                  <div className="detail-item">
                                    <span className="detail-label">Model:</span>
                                    <span className="detail-value">{run.model || '-'}</span>
                                  </div>
                                  <div className="detail-item">
                                    <span className="detail-label">Mode:</span>
                                    <span className="detail-value">{run.mode || 'GENERAL'}</span>
                                  </div>
                                  <div className="detail-item">
                                    <span className="detail-label">KB Hits:</span>
                                    <span className="detail-value">{run.kbHitCount ?? '-'}</span>
                                  </div>
                                  <div className="detail-item">
                                    <span className="detail-label">Error Code:</span>
                                    <span className="detail-value">{run.errorCode || '-'}</span>
                                  </div>
                                </div>
                              </div>
                              {run.question && (
                                <div className="detail-section full-width">
                                  <h4>Question</h4>
                                  <div className="detail-content">{run.question}</div>
                                </div>
                              )}
                              {run.answerFinal && (
                                <div className="detail-section full-width">
                                  <h4>Answer</h4>
                                  <div className="detail-content answer-content">{run.answerFinal}</div>
                                </div>
                              )}
                              <div className="detail-actions">
                                <button 
                                  className="btn btn-primary btn-sm"
                                  onClick={(e) => { e.stopPropagation(); navigate(`/telemetry/runs/${run.id}`); }}
                                >
                                  View Full Details →
                                </button>
                              </div>
                            </div>
                          </td>
                        </tr>
                      )}
                    </>
                  ))}
                </tbody>
              </table>
            </div>

            {/* Pagination */}
            {totalPages > 1 && (
              <div className="pagination">
                <button
                  disabled={page === 0}
                  onClick={() => updateFilter('page', String(page - 1))}
                >
                  ← Previous
                </button>
                {Array.from({ length: Math.min(5, totalPages) }, (_, i) => {
                  const pageNum = Math.max(0, Math.min(page - 2, totalPages - 5)) + i;
                  if (pageNum >= totalPages) return null;
                  return (
                    <button
                      key={pageNum}
                      className={page === pageNum ? 'active' : ''}
                      onClick={() => updateFilter('page', String(pageNum))}
                    >
                      {pageNum + 1}
                    </button>
                  );
                })}
                <button
                  disabled={page >= totalPages - 1}
                  onClick={() => updateFilter('page', String(page + 1))}
                >
                  Next →
                </button>
                <span className="jump-page">
                  Go to page
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
                          updateFilter('page', String(val - 1));
                          setJumpPage('');
                        }
                      }
                    }}
                  />
                  of {totalPages}
                  <button
                    className="btn btn-sm btn-primary"
                    onClick={() => {
                      const val = parseInt(jumpPage, 10);
                      if (val >= 1 && val <= totalPages) {
                        updateFilter('page', String(val - 1));
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
    </div>
  );
}

export default RunLogsDashboard;
