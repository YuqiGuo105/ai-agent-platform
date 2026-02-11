import { useState } from 'react';
import './App.css';

function App() {
  const [knowledgeBases, setKnowledgeBases] = useState([]);

  return (
    <div className="app">
      <header className="app-header">
        <h1>Agent Knowledge Base</h1>
        <p>Manage your AI agent knowledge bases</p>
      </header>
      <main className="app-main">
        {knowledgeBases.length === 0 ? (
          <p className="empty-state">No knowledge bases yet. Create one to get started.</p>
        ) : (
          <ul>
            {knowledgeBases.map((kb) => (
              <li key={kb.id}>{kb.name}</li>
            ))}
          </ul>
        )}
      </main>
    </div>
  );
}

export default App;
