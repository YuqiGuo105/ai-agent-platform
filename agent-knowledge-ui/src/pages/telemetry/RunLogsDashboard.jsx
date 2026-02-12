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
 * - Paginated results
 * - Click to view run details
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
      setRuns(data.content || []);
      setTotalPages(data.totalPages || 0);
      setTotalElements(data.totalElements || 0);
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
              <table>
                <thead>
                  <tr>
                    <th>Run ID</th>
                    <th>Session ID</th>
                    <th>Status</th>
                    <th>Query</th>
                    <th>Started At</th>
                    <th>Duration</th>
                    <th>Mode</th>
                  </tr>
                </thead>
                <tbody>
                  {runs.map((run) => (
                    <tr
                      key={run.runId}
                      className="clickable-row"
                      onClick={() => handleRowClick(run.runId)}
                    >
                      <td className="mono truncate" title={run.runId}>
                        {run.runId?.slice(0, 8)}...
                      </td>
                      <td className="mono truncate" title={run.sessionId}>
                        {run.sessionId?.slice(0, 12)}...
                      </td>
                      <td>
                        <span className={`status-badge ${getStatusClass(run.status)}`}>
                          {run.status}
                        </span>
                      </td>
                      <td className="truncate" title={run.query}>
                        {run.query || '-'}
                      </td>
                      <td>{formatDateTime(run.startTime)}</td>
                      <td>
                        {run.endTime
                          ? formatDuration(run.startTime, run.endTime)
                          : run.status === 'RUNNING'
                          ? '⏳ Running...'
                          : '-'}
                      </td>
                      <td>{run.mode || 'DEFAULT'}</td>
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
                          updateFilter('page', String(val - 1));
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
