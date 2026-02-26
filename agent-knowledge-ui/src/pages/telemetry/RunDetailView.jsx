import { useState, useEffect } from 'react';
import { useParams, Link, useNavigate } from 'react-router-dom';
import { getRunDetail, getRunToolCalls, deleteRun } from '../../services/telemetryApi';
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
  const navigate = useNavigate();
  const [run, setRun] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);
  const [expandedTools, setExpandedTools] = useState({});
  const [fetchedTools, setFetchedTools] = useState([]);  // NEW: for fallback fetch
  const [collapsedSections, setCollapsedSections] = useState({
    history: false,
    kb: false,
  });
  const [showDeleteConfirm, setShowDeleteConfirm] = useState(false);
  const [deleting, setDeleting] = useState(false);
  const [deleteError, setDeleteError] = useState(null);
  const [expandedHistoryItems, setExpandedHistoryItems] = useState({});
  const [expandedKbDocs, setExpandedKbDocs] = useState({});

  useEffect(() => {
    const fetchRunDetail = async () => {
      setLoading(true);
      setError(null);
      try {
        const data = await getRunDetail(runId);
        console.log('Run detail response:', JSON.stringify(data, null, 2));
        console.log('recentQuestionsJson:', data.recentQuestionsJson);
        console.log('kbDocIds:', data.kbDocIds);
        console.log('tools:', data.tools);
        setRun(data);
        // Fallback: if run detail has no tools, fetch separately
        if (!data.tools || data.tools.length === 0) {
          try {
            const tools = await getRunToolCalls(runId);
            console.log('Fetched tools separately:', tools);
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

  const toggleSectionCollapse = (section) => {
    setCollapsedSections((prev) => ({
      ...prev,
      [section]: !prev[section],
    }));
  };

  const toggleHistoryItem = (index) => {
    setExpandedHistoryItems((prev) => ({
      ...prev,
      [index]: !prev[index],
    }));
  };

  const toggleKbDoc = (index) => {
    setExpandedKbDocs((prev) => ({
      ...prev,
      [index]: !prev[index],
    }));
  };

  const truncateText = (text, maxLen = 80) => {
    if (!text || text.length <= maxLen) return text;
    return text.substring(0, maxLen) + '...';
  };

  const handleDelete = async () => {
    setDeleting(true);
    setDeleteError(null);
    try {
      await deleteRun(runId);
      // Redirect to run list after successful delete
      navigate('/telemetry', { replace: true });
    } catch (err) {
      setDeleteError(err.message || 'Failed to delete run');
      setDeleting(false);
    }
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
        <div className="header-actions">
          <span className={`status-badge ${getStatusClass(run.status)}`}>
            {run.status}
          </span>
          <button 
            className="delete-btn" 
            onClick={() => setShowDeleteConfirm(true)}
            title="Delete this run"
          >
            🗑️ Delete
          </button>
        </div>
      </div>

      {/* Delete Confirmation Modal */}
      {showDeleteConfirm && (
        <div className="modal-overlay" onClick={() => !deleting && setShowDeleteConfirm(false)}>
          <div className="modal-content" onClick={(e) => e.stopPropagation()}>
            <div className="modal-header">
              <h3>⚠️ Delete Run Log</h3>
            </div>
            <div className="modal-body">
              <p>Are you sure you want to delete this run log?</p>
              <p><strong>Run ID:</strong> <code>{run.runId}</code></p>
              <p className="warning-text">This will delete the run log and associated tool call/event records. Your KB documents will NOT be affected.</p>
              {deleteError && (
                <div className="error-box">{deleteError}</div>
              )}
            </div>
            <div className="modal-footer">
              <button 
                className="cancel-btn" 
                onClick={() => setShowDeleteConfirm(false)}
                disabled={deleting}
              >
                Cancel
              </button>
              <button 
                className="confirm-delete-btn" 
                onClick={handleDelete}
                disabled={deleting}
              >
                {deleting ? 'Deleting...' : 'Delete Run Log'}
              </button>
            </div>
          </div>
        </div>
      )}

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
      <div className="card">
        <div 
          className="card-header" 
          onClick={() => toggleSectionCollapse('history')}
          style={{ cursor: 'pointer', userSelect: 'none' }}
        >
          <span className="card-title">
            {collapsedSections.history ? '▶' : '▼'} 📜 Conversation History ({run.historyCount ?? 0} messages)
          </span>
        </div>
        {!collapsedSections.history && (
          <div className="expanded-content">
            {run.recentQuestionsJson ? (() => {
              try {
                const questions = JSON.parse(run.recentQuestionsJson);
                if (questions.length === 0) {
                  return <span style={{ color: '#9ca3af' }}>No prior conversation history</span>;
                }
                return (
                  <div style={{ display: 'flex', flexDirection: 'column', gap: '8px' }}>
                    {questions.map((q, i) => (
                      <div 
                        key={i} 
                        className="collapsible-item"
                        style={{ 
                          background: '#f9fafb', 
                          borderRadius: '6px', 
                          border: '1px solid #e5e7eb',
                          overflow: 'hidden'
                        }}
                      >
                        <div 
                          onClick={() => toggleHistoryItem(i)}
                          style={{ 
                            padding: '10px 12px', 
                            cursor: 'pointer', 
                            userSelect: 'none',
                            display: 'flex',
                            alignItems: 'center',
                            gap: '8px'
                          }}
                        >
                          <span style={{ color: '#6b7280', fontSize: '0.85rem' }}>
                            {expandedHistoryItems[i] ? '▼' : '▶'}
                          </span>
                          <span style={{ fontWeight: 500, color: '#374151', fontSize: '0.9rem' }}>
                            Message {i + 1}
                          </span>
                          {!expandedHistoryItems[i] && (
                            <span style={{ color: '#9ca3af', fontSize: '0.85rem', marginLeft: '8px' }}>
                              {truncateText(q, 60)}
                            </span>
                          )}
                        </div>
                        {expandedHistoryItems[i] && (
                          <div style={{ 
                            padding: '12px', 
                            borderTop: '1px solid #e5e7eb',
                            background: 'white',
                            whiteSpace: 'pre-wrap',
                            fontSize: '0.9rem',
                            lineHeight: '1.5'
                          }}>
                            {q}
                          </div>
                        )}
                      </div>
                    ))}
                  </div>
                );
              } catch { return <span style={{ color: '#9ca3af' }}>Unable to parse history</span>; }
            })() : (
              <span style={{ color: '#9ca3af' }}>No conversation history available</span>
            )}
          </div>
        )}
      </div>

      {/* KB Context */}
      <div className="card">
        <div 
          className="card-header"
          onClick={() => toggleSectionCollapse('kb')}
          style={{ cursor: 'pointer', userSelect: 'none' }}
        >
          <span className="card-title">
            {collapsedSections.kb ? '▶' : '▼'} 📂 KB Context — {run.kbHitCount ?? 0} hits{run.kbLatencyMs ? ` · ${run.kbLatencyMs}ms` : ''}
          </span>
        </div>
        {!collapsedSections.kb && (
          <div className="expanded-content">
            {run.kbDocIds ? (() => {
              const docIds = run.kbDocIds.split(',').map(id => id.trim());
              // Split KB content by document separator (format: "---" between docs)
              const docContents = run.kbContextText 
                ? run.kbContextText.split(/\n\n---\n\n/).filter(s => s.trim())
                : [];
              
              return (
                <div style={{ display: 'flex', flexDirection: 'column', gap: '8px' }}>
                  {docIds.map((docId, i) => {
                    const content = docContents[i] || '';
                    // Extract title (format: "### Title\n\n...")
                    const titleMatch = content.match(/^###\s*(.+?)(?:\n|$)/);
                    const title = titleMatch ? titleMatch[1].trim() : null;
                    const bodyContent = title 
                      ? content.replace(/^###\s*.+?\n\n?/, '').trim() 
                      : content.trim();
                    
                    return (
                      <div 
                        key={i} 
                        className="collapsible-item"
                        style={{ 
                          background: '#f9fafb', 
                          borderRadius: '6px', 
                          border: '1px solid #e5e7eb',
                          overflow: 'hidden'
                        }}
                      >
                        <div 
                          onClick={() => toggleKbDoc(i)}
                          style={{ 
                            padding: '10px 12px', 
                            cursor: 'pointer', 
                            userSelect: 'none',
                            display: 'flex',
                            alignItems: 'center',
                            gap: '8px',
                            flexWrap: 'wrap'
                          }}
                        >
                          <span style={{ color: '#6b7280', fontSize: '0.85rem' }}>
                            {expandedKbDocs[i] ? '▼' : '▶'}
                          </span>
                          <span style={{ 
                            fontFamily: 'monospace', 
                            background: '#e5e7eb', 
                            padding: '2px 8px', 
                            borderRadius: '4px',
                            fontSize: '0.8rem'
                          }}>
                            {docId}
                          </span>
                          {title && (
                            <span style={{ fontWeight: 500, color: '#374151', fontSize: '0.9rem' }}>
                              {title}
                            </span>
                          )}
                          {!expandedKbDocs[i] && bodyContent && (
                            <span style={{ color: '#9ca3af', fontSize: '0.85rem', flex: '1 1 100%', marginTop: '4px' }}>
                              {truncateText(bodyContent, 80)}
                            </span>
                          )}
                        </div>
                        {expandedKbDocs[i] && (
                          <div style={{ 
                            padding: '12px', 
                            borderTop: '1px solid #e5e7eb',
                            background: 'white',
                            whiteSpace: 'pre-wrap',
                            fontSize: '0.9rem',
                            lineHeight: '1.5',
                            maxHeight: '300px',
                            overflow: 'auto'
                          }}>
                            {bodyContent || <span style={{ color: '#f97316', fontStyle: 'italic' }}>Content not available</span>}
                          </div>
                        )}
                      </div>
                    );
                  })}
                </div>
              );
            })() : (
              <span style={{ color: '#9ca3af' }}>No KB documents retrieved for this query</span>
            )}
          </div>
        )}
      </div>

      {/* Query */}
      <div className="card">
        <div className="card-header">
          <span className="card-title">❓ Query</span>
        </div>
        <div className="expanded-content query-result-text">
          {run.question || 'No query recorded'}
        </div>
      </div>

      {/* Answer */}
      {run.answerFinal && (
        <div className="card">
          <div className="card-header">
            <span className="card-title">💬 Answer</span>
          </div>
          <div className="expanded-content query-result-text" style={{ whiteSpace: 'pre-wrap' }}>
            {run.answerFinal}
          </div>
        </div>
      )}

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
