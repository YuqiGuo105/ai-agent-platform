import { useState, useEffect } from 'react';
import { useParams, Link } from 'react-router-dom';
import { getRunDetail, getRunToolCalls } from '../../services/telemetryApi';
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
  const [fetchedTools, setFetchedTools] = useState([]);  // NEW: for fallback fetch

  useEffect(() => {
    const fetchRunDetail = async () => {
      setLoading(true);
      setError(null);
      try {
        const data = await getRunDetail(runId);
        setRun(data);
        // Fallback: if run detail has no tools, fetch separately
        if (!data.tools || data.tools.length === 0) {
          try {
            const tools = await getRunToolCalls(runId);
            setFetchedTools(tools || []);
          } catch (toolErr) {
            // Non-fatal: tool fetch failure doesn't break the page
            console.warn('Failed to fetch tool calls separately:', toolErr);
          }
        }
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
            <span className="metadata-separator">: </span>
            <span className="metadata-value mono">{run.runId}</span>
          </div>
          <div className="metadata-item">
            <span className="metadata-label">Session ID</span>
            <span className="metadata-separator">: </span>
            <span className="metadata-value mono">{run.sessionId}</span>
          </div>
          <div className="metadata-item">
            <span className="metadata-label">Mode</span>
            <span className="metadata-separator">: </span>
            <span className="metadata-value">{run.mode || 'DEFAULT'}</span>
          </div>
          <div className="metadata-item">
            <span className="metadata-label">Started At</span>
            <span className="metadata-separator">: </span>
            <span className="metadata-value">{formatDateTime(run.startTime)}</span>
          </div>
          <div className="metadata-item">
            <span className="metadata-label">Ended At</span>
            <span className="metadata-separator">: </span>
            <span className="metadata-value">{formatDateTime(run.endTime) || '-'}</span>
          </div>
          <div className="metadata-item">
            <span className="metadata-label">Duration</span>
            <span className="metadata-separator">: </span>
            <span className="metadata-value">
              {run.totalLatencyMs ? `${run.totalLatencyMs}ms` : (run.status === 'RUNNING' ? '⏳ Running...' : '-')}
            </span>
          </div>
        </div>
      </div>

      {/* Enriched Metrics */}
      <div className="card">
        <div className="card-header">
          <span className="card-title">📊 Execution Metrics</span>
        </div>
        <div className="metadata-grid">
          {run.complexityScore !== null && run.complexityScore !== undefined && (
            <div className="metadata-item">
              <span className="metadata-label">Complexity Score</span>
              <span className="metadata-value">{run.complexityScore.toFixed(3)}</span>
            </div>
          )}
          {run.executionMode && (
            <div className="metadata-item">
              <span className="metadata-label">Execution Mode</span>
              <span className="metadata-value">{run.executionMode}</span>
            </div>
          )}
          {run.deepRoundsUsed !== null && run.deepRoundsUsed !== undefined && (
            <div className="metadata-item">
              <span className="metadata-label">Deep Rounds Used</span>
              <span className="metadata-value">{run.deepRoundsUsed}</span>
            </div>
          )}
          {run.toolCallsCount !== null && run.toolCallsCount !== undefined && (
            <div className="metadata-item">
              <span className="metadata-label">Tool Calls Count</span>
              <span className="metadata-value">{run.toolCallsCount}</span>
            </div>
          )}
          {run.toolSuccessRate !== null && run.toolSuccessRate !== undefined && (
            <div className="metadata-item">
              <span className="metadata-label">Tool Success Rate</span>
              <span className="metadata-value">{(run.toolSuccessRate * 100).toFixed(1)}%</span>
            </div>
          )}
          {run.kbHitCount !== null && run.kbHitCount !== undefined && (
            <div className="metadata-item">
              <span className="metadata-label">KB Hits</span>
              <span className="metadata-value">{run.kbHitCount}</span>
            </div>
          )}
        </div>
        
        {run.featureBreakdownJson && (
          <div style={{ marginTop: '16px', padding: '0 16px 16px' }}>
            <strong style={{ fontSize: '0.9rem', color: '#6b7280' }}>Feature Breakdown:</strong>
            <div className="expanded-content" style={{ marginTop: '8px' }}>
              {formatJson(run.featureBreakdownJson)}
            </div>
          </div>
        )}
      </div>

      {/* Conversation History */}
      {(run.historyCount > 0 || run.recentQuestionsJson) && (
        <div className="card">
          <div className="card-header">
            <span className="card-title">📜 Conversation History ({run.historyCount ?? 0} messages)</span>
          </div>
          <div className="expanded-content">
            {run.recentQuestionsJson && (() => {
              try {
                const questions = JSON.parse(run.recentQuestionsJson);
                return (
                  <ol style={{ margin: 0, paddingLeft: '20px' }}>
                    {questions.map((q, i) => <li key={i} style={{ marginBottom: '6px' }}>{q}</li>)}
                  </ol>
                );
              } catch { return <span>Unable to parse history</span>; }
            })()}
          </div>
        </div>
      )}

      {/* KB Context */}
      {run.kbDocIds && (
        <div className="card">
          <div className="card-header">
            <span className="card-title">📂 KB Context (CTX) — {run.kbHitCount ?? 0} hits{run.kbLatencyMs ? ` · ${run.kbLatencyMs}ms` : ''}</span>
          </div>
          <div className="expanded-content">
            {run.kbDocIds.split(',').map((id, i) => (
              <span key={i} style={{ display: 'inline-block', background: '#f3f4f6', borderRadius: '4px', padding: '2px 8px', margin: '2px', fontFamily: 'monospace', fontSize: '0.8rem' }}>
                {id.trim()}
              </span>
            ))}
          </div>
        </div>
      )}

      {/* Query */}
      <div className="card">
        <div className="card-header">
          <span className="card-title">❓ Query</span>
        </div>
        <div className="expanded-content query-result-text">
          {run.question || 'No query recorded'}
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
      {(() => {
        const displayTools = (run.tools && run.tools.length > 0) ? run.tools : fetchedTools;
        return (
          <div className="card">
            <div className="card-header">
              <span className="card-title">🔧 Tool Calls ({displayTools.length})</span>
            </div>
            
            {displayTools.length === 0 ? (
              <div className="empty-state">
                <p>No tool calls recorded for this run</p>
              </div>
            ) : (
              <div className="timeline">
                {displayTools.map((tool, index) => (
              <div
                key={tool.toolCallId || index}
                className={`timeline-item ${tool.ok === false ? 'error' : 'success'}`}
              >
                <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start' }}>
                  <div>
                    <strong>{tool.toolName}</strong>
                    {tool.freshness && (
                      <span className={`freshness-badge ${getFreshnessClass(tool.freshness)}`} style={{ marginLeft: '8px' }}>
                        {tool.freshness}
                      </span>
                    )}
                    <div style={{ fontSize: '0.85rem', color: '#6b7280', marginTop: '4px' }}>
                      {formatDateTime(tool.sourceTs)}
                      {tool.durationMs && ` • ${tool.durationMs}ms`}
                      {tool.attempt > 1 && ` • attempt #${tool.attempt}`}
                      {tool.cacheHit !== null && tool.cacheHit !== undefined && ` • ${tool.cacheHit ? '⚡ cache hit' : '🔄 cache miss'}`}
                    </div>
                  </div>
                  <button
                    className="btn btn-sm btn-secondary"
                    onClick={() => toggleToolExpand(tool.toolCallId || index)}
                  >
                    {expandedTools[tool.toolCallId || index] ? '▼ Collapse' : '▶ Expand'}
                  </button>
                </div>

                {expandedTools[tool.toolCallId || index] && (
                  <div style={{ marginTop: '12px' }}>
                    {tool.argsPreview && (
                      <div style={{ marginBottom: '12px' }}>
                        <strong style={{ fontSize: '0.85rem', color: '#6b7280' }}>Input:</strong>
                        <div className="expanded-content">
                          {formatJson(tool.argsPreview)}
                        </div>
                      </div>
                    )}
                    {tool.resultPreview && (
                      <div style={{ marginBottom: '12px' }}>
                        <strong style={{ fontSize: '0.85rem', color: '#6b7280' }}>Output:</strong>
                        <div className="expanded-content">
                          {formatJson(tool.resultPreview)}
                        </div>
                      </div>
                    )}
                    {tool.keyInfoJson && (
                      <div style={{ marginBottom: '12px' }}>
                        <strong style={{ fontSize: '0.85rem', color: '#6b7280' }}>📋 Key Info:</strong>
                        <div className="expanded-content" style={{ marginTop: '4px' }}>
                          {formatJson(tool.keyInfoJson)}
                        </div>
                      </div>
                    )}
                    {tool.errorMsg && (
                      <div style={{ marginTop: '12px' }}>
                        <strong style={{ fontSize: '0.85rem', color: '#ef4444' }}>Error:</strong>
                        <div className="error-box" style={{ marginTop: '4px' }}>
                          {tool.errorMsg}
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
        );
      })()}
    </div>
  );
}

export default RunDetailView;
