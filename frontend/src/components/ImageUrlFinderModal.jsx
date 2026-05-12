import { useEffect, useMemo, useState } from 'react';
import AnimatedModal from './AnimatedModal.jsx';

export function buildDuckDuckGoImageSearchUrl(query) {
  const searchQuery = encodeURIComponent(query || 'image');
  return `https://duckduckgo.com/?q=${searchQuery}&iar=images&iax=images&ia=images`;
}

function normalizeCandidate(candidate) {
  const imageUrl = candidate.imageUrl || candidate.image_url || candidate.logo_url || '';
  const title = candidate.title || candidate.name || candidate.domain || 'Image';
  const subtitle = candidate.subtitle || candidate.domain || candidate.source || '';
  const key = candidate.key || candidate.id || candidate.slug || candidate.domain || imageUrl || title;
  return {
    ...candidate,
    key,
    imageUrl,
    title,
    subtitle
  };
}

export function ImageUrlFinderPanel({
  initialQuery = '',
  initialUrl = '',
  autoSearch = false,
  searchLabel = 'Image Search',
  searchPlaceholder = 'Search by name',
  searchButtonLabel = 'Search',
  searchingLabel = 'Searching...',
  urlLabel = 'Image URL',
  urlPlaceholder = 'https://example.com/image.png',
  emptyMessage = 'No matches found. Try a simpler search or paste a direct image URL.',
  previewLabel = 'Selected image preview',
  externalSearchLabel = 'DuckDuckGo Images',
  externalSearchQuery,
  showExternalSearchInTools = true,
  onSearch,
  onSelectedUrlChange,
  onSave,
  onCancel,
  secondaryActions = [],
  showActions = false,
  saveLabel = 'Save',
  savingLabel = 'Saving...',
  close,
  imageVariant = 'logo',
  className = ''
}) {
  const [query, setQuery] = useState(initialQuery || '');
  const [imageUrl, setImageUrl] = useState(initialUrl || '');
  const [candidates, setCandidates] = useState([]);
  const [searching, setSearching] = useState(false);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState('');
  const [hasSearched, setHasSearched] = useState(false);
  const hasProviderSearch = typeof onSearch === 'function';
  const hasSecondaryActions = showExternalSearchInTools || secondaryActions.length > 0;

  useEffect(() => {
    setImageUrl(initialUrl || '');
  }, [initialUrl]);

  const normalizedCandidates = useMemo(
    () => candidates.map(normalizeCandidate).filter((candidate) => candidate.imageUrl),
    [candidates]
  );

  function updateImageUrl(nextUrl) {
    setImageUrl(nextUrl);
    onSelectedUrlChange?.(nextUrl);
  }

  async function runSearch(nextQuery = query, options = {}) {
    const trimmed = nextQuery.trim();
    if (!trimmed) {
      setError('Enter a search term.');
      return;
    }
    if (!hasProviderSearch) {
      openExternalSearch();
      return;
    }
    const isActive = options.activeRef || (() => true);
    setSearching(true);
    setError('');
    try {
      const result = await onSearch(trimmed, { activeRef: isActive });
      if (!isActive()) return;
      setCandidates(result?.candidates || []);
      setHasSearched(true);
      setError(result?.message || '');
    } catch (err) {
      if (!isActive()) return;
      setCandidates([]);
      setHasSearched(true);
      setError(err.message || 'Image search failed.');
    } finally {
      if (isActive()) setSearching(false);
    }
  }

  useEffect(() => {
    if (!autoSearch || !initialQuery || !hasProviderSearch) return undefined;
    let active = true;
    runSearch(initialQuery, { activeRef: () => active });
    return () => {
      active = false;
    };
  }, []); // eslint-disable-line react-hooks/exhaustive-deps

  function openExternalSearch() {
    const searchText = typeof externalSearchQuery === 'function'
      ? externalSearchQuery(query)
      : `${query || initialQuery || 'image'} image`;
    window.open(buildDuckDuckGoImageSearchUrl(searchText), '_blank', 'noopener,noreferrer');
  }

  async function handleSave() {
    const trimmed = imageUrl.trim();
    if (!trimmed) {
      setError('Choose a result or paste a direct image URL.');
      return;
    }
    if (!onSave) return;
    setSaving(true);
    setError('');
    try {
      await onSave(trimmed, { close, setError });
    } catch (err) {
      setError(err.message || 'Save failed.');
      setSaving(false);
    }
  }

  async function handleSecondaryAction(action) {
    setSaving(true);
    setError('');
    try {
      await action.onClick?.({ close, imageUrl, query, setError });
      if (!action.keepSaving) setSaving(false);
    } catch (err) {
      setError(err.message || `${action.label} failed.`);
      setSaving(false);
    }
  }

  return (
    <div className={`image-finder ${className}`.trim()}>
      <div className="image-finder-search">
        <label className="field">
          <span>{searchLabel}</span>
          <input
            type="search"
            value={query}
            onChange={(event) => setQuery(event.target.value)}
            onKeyDown={(event) => {
              if (event.key === 'Enter') {
                event.preventDefault();
                runSearch(query);
              }
            }}
            placeholder={searchPlaceholder}
          />
        </label>
        <button type="button" className="btn-secondary" onClick={() => runSearch(query)} disabled={hasProviderSearch && searching}>
          {hasProviderSearch && searching ? searchingLabel : hasProviderSearch ? searchButtonLabel : externalSearchLabel}
        </button>
      </div>

      <div className="image-finder-results">
        {normalizedCandidates.map((candidate) => (
          <button
            key={candidate.key}
            type="button"
            className={`image-candidate image-candidate-${imageVariant} ${imageUrl === candidate.imageUrl ? 'selected' : ''}`}
            onClick={() => updateImageUrl(candidate.imageUrl)}
          >
            <span className={`image-candidate-image image-candidate-image-${imageVariant}`}>
              <img src={candidate.imageUrl} alt="" loading="lazy" />
            </span>
            <span className="image-candidate-text">
              <strong>{candidate.title}</strong>
              {candidate.subtitle && <span>{candidate.subtitle}</span>}
            </span>
          </button>
        ))}
        {!searching && hasSearched && normalizedCandidates.length === 0 && (
          <div className="image-finder-empty">
            {emptyMessage}
          </div>
        )}
      </div>

      <label className="field">
        <span>{urlLabel}</span>
        <input
          type="url"
          value={imageUrl}
          onChange={(event) => updateImageUrl(event.target.value)}
          placeholder={urlPlaceholder}
        />
      </label>

      <div className={`image-finder-bottom-tools image-finder-bottom-tools-${imageVariant} ${imageUrl ? 'has-preview' : ''} ${hasSecondaryActions ? 'has-actions' : ''}`}>
        {imageUrl && (
          <div className={`image-finder-preview image-finder-preview-${imageVariant}`} aria-label={previewLabel}>
            <img src={imageUrl} alt="" />
          </div>
        )}
        {hasSecondaryActions && (
          <div className="image-finder-secondary-actions">
            {showExternalSearchInTools && (
              <button type="button" className="btn-secondary" onClick={openExternalSearch}>
                {externalSearchLabel}
              </button>
            )}
            {secondaryActions.map((action) => (
              <button
                key={action.label}
                type="button"
                className="btn-secondary"
                onClick={() => handleSecondaryAction(action)}
                disabled={saving || action.disabled}
              >
                {action.label}
              </button>
            ))}
          </div>
        )}
      </div>

      {error && <div className="error">{error}</div>}

      {showActions && (
        <div className="modal-actions image-finder-actions">
          <button type="button" className="btn-secondary" onClick={onCancel || close}>
            Cancel
          </button>
          <button type="button" className="btn-primary" onClick={handleSave} disabled={saving}>
            {saving ? savingLabel : saveLabel}
          </button>
        </div>
      )}
    </div>
  );
}

export default function ImageUrlFinderModal({
  title,
  copy,
  onClose,
  size = 'lg',
  ...panelProps
}) {
  return (
    <AnimatedModal onClose={onClose} size={size}>
      {({ close }) => (
        <>
          {title && <h3>{title}</h3>}
          {copy && <p className="modal-copy">{copy}</p>}
          <ImageUrlFinderPanel
            {...panelProps}
            close={close}
            onCancel={close}
            showActions
          />
        </>
      )}
    </AnimatedModal>
  );
}
