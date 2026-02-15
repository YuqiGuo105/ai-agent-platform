import { useState } from 'react';
import './ChunkPreviewList.css';

export default function ChunkPreviewList({ chunks }) {
  const [expanded, setExpanded] = useState([]);

  if (!chunks?.length) return null;

  const toggle = (idx) => {
    setExpanded((prev) =>
      prev.includes(idx) ? prev.filter((i) => i !== idx) : [...prev, idx]
    );
  };

  return (
    <div className="chunk-list">
      {chunks.map((chunk) => {
        const isOpen = expanded.includes(chunk.index);
        return (
          <div key={chunk.index} className="chunk-card">
            <div className="chunk-header" onClick={() => toggle(chunk.index)}>
              <span className="pill">#{chunk.index}</span>
              <span className="pill muted">{chunk.charCount} chars</span>
              <button className="link-btn">{isOpen ? 'Collapse' : 'Expand'}</button>
            </div>
            <div className={`chunk-content ${isOpen ? 'open' : ''}`}>
              {chunk.content}
            </div>
          </div>
        );
      })}
    </div>
  );
}
