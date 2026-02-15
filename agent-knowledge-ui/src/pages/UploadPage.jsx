import { useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import api from '../axios';
import { useAuth } from '../contexts/AuthContext';
import ChunkPreviewList from '../components/ChunkPreviewList';
import './UploadPage.css';

export default function UploadPage() {
  const { user, logout } = useAuth();
  const navigate = useNavigate();

  const [mode, setMode] = useState('file');
  const [file, setFile] = useState(null);
  const [text, setText] = useState('');
  const [metadataInput, setMetadataInput] = useState('');
  const [chunkResponse, setChunkResponse] = useState(null);
  const [isProcessing, setIsProcessing] = useState(false);
  const [saving, setSaving] = useState(false);
  const [message, setMessage] = useState(null);

  const parsedMetadata = () => {
    if (!metadataInput.trim()) return {};
    try {
      return JSON.parse(metadataInput);
    } catch (e) {
      throw new Error('Metadata must be valid JSON');
    }
  };

  const clearState = () => {
    setFile(null);
    setText('');
    setChunkResponse(null);
    setMessage(null);
  };

  const handleUpload = async () => {
    setMessage(null);
    if (mode === 'file' && !file) {
      setMessage({ type: 'error', text: 'Please choose a file first.' });
      return;
    }
    if (mode === 'text' && !text.trim()) {
      setMessage({ type: 'error', text: 'Please enter text content.' });
      return;
    }

    let meta;
    try {
      meta = parsedMetadata();
    } catch (err) {
      setMessage({ type: 'error', text: err.message });
      return;
    }

    setIsProcessing(true);
    try {
      if (mode === 'file') {
        // 1) Get presigned URL
        const presign = await api.post('/kb/upload/presign', {
          filename: file.name,
          contentType: file.type || 'application/octet-stream',
        });

        const { uploadUrl, fileUrl, method } = presign.data;

        // 2) Upload to S3 via presigned URL
        const putResp = await fetch(uploadUrl, {
          method: method || 'PUT',
          headers: {
            'Content-Type': file.type || 'application/octet-stream',
          },
          body: file,
        });
        if (!putResp.ok) {
          throw new Error('Failed to upload file to storage');
        }

        // 3) Ask backend to extract + chunk
        const chunkRes = await api.post('/kb/upload', {
          fileUrl,
          metadata: meta,
        });
        setChunkResponse(chunkRes.data);
      } else {
        const chunkRes = await api.post('/kb/upload/text', {
          text,
          metadata: meta,
        });
        setChunkResponse(chunkRes.data);
      }
    } catch (err) {
      const errMsg = err.response?.data?.error || err.message || 'Upload failed';
      setMessage({ type: 'error', text: errMsg });
    } finally {
      setIsProcessing(false);
    }
  };

  const handleSave = async () => {
    if (!chunkResponse?.chunks?.length) return;
    setSaving(true);
    setMessage(null);
    try {
      const payload = {
        docType: chunkResponse.docType,
        metadata: parsedMetadata(),
        chunks: chunkResponse.chunks.map((c) => ({
          index: c.index,
          content: c.content,
          embedding: c.embedding,
          metadata: { charCount: c.charCount },
        })),
      };
      const res = await api.post('/kb/upload/save', payload);
      setMessage({ type: 'success', text: `Saved ${res.data.saved} chunks.` });
      setChunkResponse(null);
    } catch (err) {
      const errMsg = err.response?.data?.error || err.message || 'Save failed';
      setMessage({ type: 'error', text: errMsg });
    } finally {
      setSaving(false);
    }
  };

  return (
    <div className="upload-page">
      <nav className="topbar">
        <div className="topbar-left">
          <h1 className="topbar-title">Upload to Knowledge</h1>
          <Link to="/" className="topbar-link">← Back</Link>
        </div>
        <div className="topbar-right">
          <span className="user-label">{user?.displayName || user?.email || 'User'}</span>
          <Link to="/telemetry" className="btn-logs">📊 Run Logs</Link>
          <button className="btn-logout" onClick={logout}>Sign Out</button>
        </div>
      </nav>

      <main className="upload-main">
        <section className="card">
          <header className="card-header">
            <div className="mode-toggle">
              <button className={mode === 'file' ? 'active' : ''} onClick={() => setMode('file')}>📁 File</button>
              <button className={mode === 'text' ? 'active' : ''} onClick={() => setMode('text')}>📝 Text</button>
            </div>
            <div className="actions">
              <button className="ghost" onClick={() => navigate('/')}>Cancel</button>
              <button className="primary" onClick={handleUpload} disabled={isProcessing}>
                {isProcessing ? 'Processing…' : 'Generate chunks'}
              </button>
            </div>
          </header>

          {mode === 'file' ? (
            <div className="upload-body">
              <label className="dropzone">
                <input
                  type="file"
                  accept=".pdf,.doc,.docx,.txt"
                  onChange={(e) => setFile(e.target.files?.[0] || null)}
                />
                {file ? (
                  <div className="file-chip">📄 {file.name} ({(file.size / 1024).toFixed(1)} KB)</div>
                ) : (
                  <p>Drop a file here or click to browse</p>
                )}
              </label>
            </div>
          ) : (
            <div className="upload-body">
              <textarea
                value={text}
                onChange={(e) => setText(e.target.value)}
                rows={8}
                placeholder="Paste or type text to chunk"
              />
            </div>
          )}

          <div className="metadata-block">
            <label>Metadata (JSON, optional)</label>
            <textarea
              rows={3}
              value={metadataInput}
              onChange={(e) => setMetadataInput(e.target.value)}
              placeholder='{"source":"manual"}'
            />
          </div>

          {message && (
            <div className={`banner ${message.type}`}>
              {message.text}
            </div>
          )}
        </section>

        {chunkResponse?.chunks?.length ? (
          <section className="card">
            <header className="card-header">
              <div>
                <div className="eyebrow">Detected type: {chunkResponse.docType}</div>
                <h3>{chunkResponse.chunks.length} chunks generated</h3>
              </div>
              <div className="actions">
                <button className="ghost" onClick={clearState}>Discard</button>
                <button className="primary" onClick={handleSave} disabled={saving}>
                  {saving ? 'Saving…' : 'Save all chunks'}
                </button>
              </div>
            </header>
            <ChunkPreviewList chunks={chunkResponse.chunks} />
          </section>
        ) : null}
      </main>
    </div>
  );
}
