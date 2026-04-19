import { useState, useRef } from 'react';

export default function Import({ onComplete }) {
  const [file, setFile] = useState(null);
  const [dragging, setDragging] = useState(false);
  const [importing, setImporting] = useState(false);
  const [result, setResult] = useState(null);
  const [error, setError] = useState('');
  const fileInputRef = useRef(null);

  function handleFile(f) {
    if (!f) return;
    if (!f.name.toLowerCase().endsWith('.csv')) {
      setError('Please choose a .csv file.');
      return;
    }
    if (f.size > 20 * 1024 * 1024) {
      setError('File is larger than 20 MB.');
      return;
    }
    setError('');
    setFile(f);
  }

  function handleDrop(e) {
    e.preventDefault();
    setDragging(false);
    const f = e.dataTransfer.files?.[0];
    handleFile(f);
  }

  async function handleImport() {
    if (!file) return;
    setImporting(true);
    setError('');
    setResult(null);

    try {
      const form = new FormData();
      form.append('file', file);

      const res = await fetch('/api/import/rocket-money', {
        method: 'POST',
        credentials: 'same-origin',
        body: form
      });

      const data = await res.json().catch(() => ({}));
      if (!res.ok) {
        throw new Error(data.error || `Import failed: ${res.status}`);
      }

      setResult(data);
    } catch (err) {
      setError(err.message || 'Import failed');
    } finally {
      setImporting(false);
    }
  }

  if (result) {
    return (
      <div className="import-view">
        <div className="import-summary">
          <div className="import-summary-icon">✓</div>
          <h2>Import complete</h2>

          <dl className="stat-grid">
            <div><dt>Imported</dt><dd>{result.inserted.toLocaleString()}</dd></div>
            <div><dt>Skipped</dt><dd>{result.skipped.toLocaleString()}</dd></div>
            <div><dt>Accounts</dt><dd>{result.accountsCreated.toLocaleString()}</dd></div>
            <div><dt>Rules</dt><dd>{result.rulesCreated.toLocaleString()}</dd></div>
          </dl>

          {result.parseWarnings > 0 && (
            <p className="muted">
              Parser reported {result.parseWarnings} minor warnings — usually fine.
            </p>
          )}

          <button type="button" className="btn-primary" onClick={onComplete}>
            View transactions
          </button>
        </div>
      </div>
    );
  }

  return (
    <div className="import-view">
      <div className="import-card">
        <h2>Import from Rocket Money</h2>
        <p>
          Export your transactions from Rocket Money as CSV, then drop the file
          below. Accounts will be created, Custom Names preserved, and rename
          rules generated automatically.
        </p>

        <div
          className={`drop-zone ${dragging ? 'dragging' : ''} ${file ? 'has-file' : ''}`}
          onDragOver={(e) => {
            e.preventDefault();
            setDragging(true);
          }}
          onDragLeave={() => setDragging(false)}
          onDrop={handleDrop}
          onClick={() => fileInputRef.current?.click()}
          role="button"
          tabIndex={0}
        >
          <input
            ref={fileInputRef}
            type="file"
            accept=".csv,text/csv"
            onChange={(e) => handleFile(e.target.files?.[0])}
            style={{ display: 'none' }}
          />
          {file ? (
            <>
              <div className="drop-zone-icon">✓</div>
              <div className="drop-zone-filename">{file.name}</div>
              <div className="subtle">{(file.size / 1024).toFixed(0)} KB — click to pick a different file</div>
            </>
          ) : (
            <>
              <div className="drop-zone-icon">↑</div>
              <div className="drop-zone-primary">Drop your CSV here</div>
              <div className="subtle">or click to browse</div>
            </>
          )}
        </div>

        {error && <div className="error">{error}</div>}

        <button
          type="button"
          className="btn-primary"
          disabled={!file || importing}
          onClick={handleImport}
        >
          {importing ? (
            <>
              <span className="spinner-inline" /> Importing…
            </>
          ) : (
            'Import'
          )}
        </button>
      </div>
    </div>
  );
}
