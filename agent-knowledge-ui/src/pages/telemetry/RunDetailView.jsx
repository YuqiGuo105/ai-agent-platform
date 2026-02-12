import { useState, useEffect } from 'react';
import { useParams, Link } from 'react-router-dom';
import { getRunDetail } from '../../services/telemetryApi';
import { formatDateTime, formatDuration, formatJson, getFreshnessClass } from '../../utils/formatters';
import './TelemetryStyles.css';

/**
 * Run Detail View - Shows detailed information about a specific run
 * Features:
 * - Run metadata display
 * - Tool call timeline with expandable content
 * - Input/output viewing
 */
function RunDetailView() {
  const { runId } = useParams();
  const [run, setRun] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);
  const [expandedTools, setExpandedTools] = useState({});

  useEffect(() => {
    const fetchRunDetail = async () => {
      setLoading(true);
      setError(null);
      try {
        const data = await getRunDetail(runId);
        setRun(data);
      } catch (err) {
        setError(err.message || 'Failed to fetch run details');
      } finally {
        setLoading(false);
      }
    };

    if (runId) {
      fetchRunDetail();
    }
  }, [runId]);

  const toggleToolExpand = (toolId) => {
    setExpandedTools((prev) => ({
      ...prev,
      [toolId]: !prev[toolId],
    }));
  };

  const getStatusClass = (status) => {
    return status?.toLowerCase() || 'unknown';
  };

  if (loading) {
    return (
      <div className="telemetry-page">
        <Link to="/telemetry" className="back-link">← Back to Run Logs</Link>
        <div className="loading">
          <div className="spinner"></div>
        </div>
      </div>
    );
  }

  if (error) {
    return (
      <div className="telemetry-page">
        <Link to="/telemetry" className="back-link">← Back to Run Logs</Link>
        <div className="error-box">⚠️ {error}</div>
      </div>
    );
  }

  if (!run) {
    return (
      <div className="telemetry-page">
        <Link to="/telemetry" className="back-link">← Back to Run Logs</Link>
        <div className="empty-state">
          <div className="empty-state-icon">🔍</div>
          <p>Run not found</p>
        </div>
      </div>
    );
  }

  return (
    <div className="telemetry-page">
      <Link to="/telemetry" className="back-link">← Back to Run Logs</Link>

      <div className="telemetry-header">
        <h1>Run Detail</h1>
        <span className={`status-badge ${getStatusClass(run.status)}`}>
          {run.status}
        </span>
      </div>

      {/* Run Metadata */}
      <div className="card">
        <div className="card-header">
          <span className="card-title">📋 Run Information</span>
        </div>
        <div className="metadata-grid">
          <div className="metadata-item">
            <span className="metadata-label">Run ID</span>
            <span className="metadata-value mono">{run.runId}</span>
          </div>
          <div className="metadata-item">
            <span className="metadata-label">Session ID</span>
            <span className="metadata-value mono">{run.sessionId}</span>
          </div>
          <div className="metadata-item">
            <span className="metadata-label">Mode</span>
            <span className="metadata-value">{run.mode || 'DEFAULT'}</span>
          </div>
          <div className="metadata-item">
            <span className="metadata-label">Started At</span>
            <span className="metadata-value">{formatDateTime(run.startTime)}</span>
          </div>
          <div className="metadata-item">
            <span className="metadata-label">Ended At</span>
            <span className="metadata-value">{formatDateTime(run.endTime) || '-'}</span>
          </div>
          <div className="metadata-item">
            <span className="metadata-label">Duration</span>
            <span className="metadata-value">
              {run.endTime ? formatDuration(run.startTime, run.endTime) : '⏳ Running...'}
            </span>
          </div>
        </div>
      </div>

      {/* Query */}
      <div className="card">
        <div className="card-header">
          <span className="card-title">❓ Query</span>
        </div>
        <div className="expanded-content">
          {run.query || 'No query recorded'}
        </div>
      </div>

      {/* Error (if any) */}
      {run.errorMessage && (
        <div className="card">
          <div className="card-header">
            <span className="card-title">⚠️ Error</span>
          </div>
          <div className="error-box">
            {run.errorMessage}
          </div>
        </div>
      )}

      {/* Tool Calls Timeline */}
      <div className="card">
        <div className="card-header">
          <span className="card-title">🔧 Tool Calls ({run.toolCalls?.length || 0})</span>
        </div>
        
        {!run.toolCalls || run.toolCalls.length === 0 ? (
          <div className="empty-state">
            <p>No tool calls recorded for this run</p>
          </div>
        ) : (
          <div className="timeline">
            {run.toolCalls.map((tool, index) => (
              <div
                key={tool.id || index}
                className={`timeline-item ${tool.success === false ? 'error' : 'success'}`}
              >
                <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start' }}>
                  <div>
                    <strong>{tool.toolName}</strong>
                    {tool.cacheFreshness && (
                      <span className={`freshness-badge ${getFreshnessClass(tool.cacheFreshness)}`} style={{ marginLeft: '8px' }}>
                        {tool.cacheFreshness}
                      </span>
                    )}
                    <div style={{ fontSize: '0.85rem', color: '#6b7280', marginTop: '4px' }}>
                      {formatDateTime(tool.startTime)}
                      {tool.endTime && ` • ${formatDuration(tool.startTime, tool.endTime)}`}
                    </div>
                  </div>
                  <button
                    className="btn btn-sm btn-secondary"
                    onClick={() => toggleToolExpand(tool.id || index)}
                  >
                    {expandedTools[tool.id || index] ? '▼ Collapse' : '▶ Expand'}
                  </button>
                </div>

                {expandedTools[tool.id || index] && (
                  <div style={{ marginTop: '12px' }}>
                    {tool.input && (
                      <div style={{ marginBottom: '12px' }}>
                        <strong style={{ fontSize: '0.85rem', color: '#6b7280' }}>Input:</strong>
                        <div className="expanded-content">
                          {formatJson(tool.input)}
                        </div>
                      </div>
                    )}
                    {tool.output && (
                      <div>
                        <strong style={{ fontSize: '0.85rem', color: '#6b7280' }}>Output:</strong>
                        <div className="expanded-content">
                          {formatJson(tool.output)}
                        </div>
                      </div>
                    )}
                    {tool.errorMessage && (
                      <div style={{ marginTop: '12px' }}>
                        <strong style={{ fontSize: '0.85rem', color: '#ef4444' }}>Error:</strong>
                        <div className="error-box" style={{ marginTop: '4px' }}>
                          {tool.errorMessage}
                        </div>
                      </div>
                    )}
                  </div>
                )}
              </div>
            ))}
          </div>
        )}
      </div>
    </div>
  );
}

export default RunDetailView;
