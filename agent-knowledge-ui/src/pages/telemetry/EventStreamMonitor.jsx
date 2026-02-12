import { useState, useEffect, useRef, useCallback } from 'react';
import { Link } from 'react-router-dom';
import { createEventSource } from '../../services/telemetryApi';
import { formatDateTime, formatJson } from '../../utils/formatters';
import './TelemetryStyles.css';

/**
 * Event Stream Monitor - Real-time telemetry event viewer
 * Features:
 * - SSE connection to event stream
 * - Real-time event display
 * - Event type filtering
 * - Pause/resume functionality
 * - Event detail expansion
 */
function EventStreamMonitor() {
  // State
  const [events, setEvents] = useState([]);
  const [connected, setConnected] = useState(false);
  const [paused, setPaused] = useState(false);
  const [filter, setFilter] = useState('');
  const [expandedEvents, setExpandedEvents] = useState({});
  const [autoScroll, setAutoScroll] = useState(true);
  const [error, setError] = useState(null);
  
  // Refs
  const eventSourceRef = useRef(null);
  const eventsContainerRef = useRef(null);
  const maxEvents = 100; // Keep last 100 events

  // Connect to event stream
  const connect = useCallback(() => {
    if (eventSourceRef.current) {
      eventSourceRef.current.close();
    }

    setError(null);
    setConnected(false);

    try {
      const es = createEventSource('/events/stream');
      
      es.onopen = () => {
        setConnected(true);
        setError(null);
      };

      es.onmessage = (event) => {
        if (paused) return;
        
        try {
          const data = JSON.parse(event.data);
          const newEvent = {
            id: Date.now() + Math.random(),
            timestamp: new Date().toISOString(),
            type: data.type || 'unknown',
            data: data,
          };

          setEvents((prev) => {
            const updated = [newEvent, ...prev];
            return updated.slice(0, maxEvents);
          });
        } catch {
          // If not JSON, treat as plain text
          const newEvent = {
            id: Date.now() + Math.random(),
            timestamp: new Date().toISOString(),
            type: 'message',
            data: { message: event.data },
          };
          setEvents((prev) => [newEvent, ...prev].slice(0, maxEvents));
        }
      };

      es.onerror = () => {
        setConnected(false);
        setError('Connection lost. Attempting to reconnect...');
      };

      eventSourceRef.current = es;
    } catch (err) {
      setError(`Failed to connect: ${err.message}`);
      setConnected(false);
    }
  }, [paused]);

  // Disconnect from event stream
  const disconnect = useCallback(() => {
    if (eventSourceRef.current) {
      eventSourceRef.current.close();
      eventSourceRef.current = null;
    }
    setConnected(false);
  }, []);

  // Auto-connect on mount
  useEffect(() => {
    connect();
    return () => disconnect();
  }, [connect, disconnect]);

  // Auto-scroll to bottom
  useEffect(() => {
    if (autoScroll && eventsContainerRef.current) {
      eventsContainerRef.current.scrollTop = 0;
    }
  }, [events, autoScroll]);

  // Toggle event expansion
  const toggleExpand = (eventId) => {
    setExpandedEvents((prev) => ({
      ...prev,
      [eventId]: !prev[eventId],
    }));
  };

  // Clear events
  const clearEvents = () => {
    setEvents([]);
    setExpandedEvents({});
  };

  // Filter events
  const filteredEvents = filter
    ? events.filter((e) =>
        e.type.toLowerCase().includes(filter.toLowerCase()) ||
        JSON.stringify(e.data).toLowerCase().includes(filter.toLowerCase())
      )
    : events;

  // Get event type color
  const getEventTypeColor = (type) => {
    const colors = {
      'run.started': '#3b82f6',
      'run.completed': '#22c55e',
      'run.failed': '#ef4444',
      'tool.started': '#eab308',
      'tool.completed': '#22c55e',
      'tool.failed': '#ef4444',
      'error': '#ef4444',
    };
    return colors[type?.toLowerCase()] || '#6b7280';
  };

  return (
    <div className="telemetry-page">
      <div className="telemetry-header">
        <div>
          <Link to="/" className="back-link">← Back to Knowledge Base</Link>
          <h1>⚡ Event Stream Monitor</h1>
        </div>
        <div style={{ display: 'flex', gap: '8px', alignItems: 'center' }}>
          <span
            style={{
              display: 'inline-flex',
              alignItems: 'center',
              gap: '6px',
              padding: '4px 12px',
              borderRadius: '12px',
              backgroundColor: connected ? 'rgba(34, 197, 94, 0.15)' : 'rgba(239, 68, 68, 0.15)',
              color: connected ? '#22c55e' : '#ef4444',
              fontSize: '0.85rem',
              fontWeight: 500,
            }}
          >
            <span
              style={{
                width: '8px',
                height: '8px',
                borderRadius: '50%',
                backgroundColor: connected ? '#22c55e' : '#ef4444',
                animation: connected ? 'pulse 2s infinite' : 'none',
              }}
            />
            {connected ? 'Connected' : 'Disconnected'}
          </span>
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

      {/* Controls */}
      <div className="card">
        <div className="filter-bar">
          <div className="filter-group">
            <label>Filter</label>
            <input
              type="text"
              placeholder="Filter by type or content..."
              value={filter}
              onChange={(e) => setFilter(e.target.value)}
            />
          </div>
          <div style={{ display: 'flex', gap: '8px', alignItems: 'flex-end' }}>
            <button
              className={`btn ${paused ? 'btn-primary' : 'btn-secondary'}`}
              onClick={() => setPaused(!paused)}
            >
              {paused ? '▶️ Resume' : '⏸️ Pause'}
            </button>
            <button
              className="btn btn-secondary"
              onClick={clearEvents}
            >
              🗑️ Clear
            </button>
            {!connected && (
              <button
                className="btn btn-primary"
                onClick={connect}
              >
                🔌 Reconnect
              </button>
            )}
            <label style={{ display: 'flex', alignItems: 'center', gap: '6px', cursor: 'pointer' }}>
              <input
                type="checkbox"
                checked={autoScroll}
                onChange={(e) => setAutoScroll(e.target.checked)}
              />
              Auto-scroll
            </label>
          </div>
        </div>
      </div>

      {/* Event Stream */}
      <div className="card" style={{ maxHeight: 'calc(100vh - 350px)', overflow: 'hidden' }}>
        <div className="card-header">
          <span className="card-title">
            Events ({filteredEvents.length})
            {paused && <span style={{ color: '#eab308', marginLeft: '8px' }}>(Paused)</span>}
          </span>
        </div>

        <div
          ref={eventsContainerRef}
          style={{
            maxHeight: 'calc(100vh - 450px)',
            overflowY: 'auto',
            padding: '8px 0',
          }}
        >
          {filteredEvents.length === 0 ? (
            <div className="empty-state">
              <div className="empty-state-icon">📡</div>
              <p>{connected ? 'Waiting for events...' : 'Not connected to event stream'}</p>
            </div>
          ) : (
            <div style={{ display: 'flex', flexDirection: 'column', gap: '8px' }}>
              {filteredEvents.map((event) => (
                <div
                  key={event.id}
                  style={{
                    padding: '12px 16px',
                    backgroundColor: '#f9fafb',
                    borderRadius: '8px',
                    borderLeft: `3px solid ${getEventTypeColor(event.type)}`,
                    cursor: 'pointer',
                  }}
                  onClick={() => toggleExpand(event.id)}
                >
                  <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                    <div style={{ display: 'flex', alignItems: 'center', gap: '12px' }}>
                      <span
                        style={{
                          padding: '2px 8px',
                          borderRadius: '4px',
                          backgroundColor: getEventTypeColor(event.type),
                          color: 'white',
                          fontSize: '0.75rem',
                          fontWeight: 600,
                          textTransform: 'uppercase',
                        }}
                      >
                        {event.type}
                      </span>
                      <span style={{ fontSize: '0.85rem', color: '#6b7280' }}>
                        {formatDateTime(event.timestamp)}
                      </span>
                    </div>
                    <span style={{ color: '#6b7280', fontSize: '0.85rem' }}>
                      {expandedEvents[event.id] ? '▼' : '▶'}
                    </span>
                  </div>

                  {/* Preview */}
                  {!expandedEvents[event.id] && (
                    <div
                      style={{
                        marginTop: '8px',
                        fontSize: '0.85rem',
                        color: '#6b7280',
                        overflow: 'hidden',
                        textOverflow: 'ellipsis',
                        whiteSpace: 'nowrap',
                      }}
                    >
                      {event.data.message || event.data.runId || event.data.toolName || JSON.stringify(event.data).slice(0, 100)}
                    </div>
                  )}

                  {/* Expanded content */}
                  {expandedEvents[event.id] && (
                    <div className="expanded-content" style={{ marginTop: '12px' }}>
                      {formatJson(event.data)}
                    </div>
                  )}
                </div>
              ))}
            </div>
          )}
        </div>
      </div>

      {/* Add pulse animation */}
      <style>{`
        @keyframes pulse {
          0%, 100% { opacity: 1; }
          50% { opacity: 0.5; }
        }
      `}</style>
    </div>
  );
}

export default EventStreamMonitor;
