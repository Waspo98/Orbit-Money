import {
  DndContext,
  closestCenter
} from '@dnd-kit/core';
import {
  SortableContext,
  arrayMove,
  verticalListSortingStrategy
} from '@dnd-kit/sortable';
import { useMemo, useState } from 'react';
import AnimatedModal from '../AnimatedModal.jsx';
import ReorderListItem, {
  useDragInteractionLock,
  useReorderSensors
} from '../ReorderListItem.jsx';
import {
  DASHBOARD_CARD_DEFS,
  normalizeDashboardLayout
} from './dashboardConfig.js';

export default function DashboardCustomizeModal({ layout, onChange, onClose }) {
  const sensors = useReorderSensors();
  const [draggingId, setDraggingId] = useState(null);
  useDragInteractionLock(!!draggingId);

  function toggleCard(id) {
    onChange((prev) =>
      normalizeDashboardLayout(prev).map((item) =>
        item.id === id ? { ...item, visible: !item.visible } : item
      )
    );
  }

  function updateCardWideSpan(id, wideSpan) {
    onChange((prev) =>
      normalizeDashboardLayout(prev).map((item) =>
        item.id === id ? { ...item, wideSpan } : item
      )
    );
  }

  function handleDragEnd(event) {
    const { active, over } = event;
    setDraggingId(null);
    if (!over || active.id === over.id) return;
    onChange((prev) => {
      const normalized = normalizeDashboardLayout(prev);
      const oldIndex = normalized.findIndex((item) => item.id === active.id);
      const newIndex = normalized.findIndex((item) => item.id === over.id);
      if (oldIndex < 0 || newIndex < 0) return normalized;
      return arrayMove(normalized, oldIndex, newIndex);
    });
  }

  const normalizedLayout = normalizeDashboardLayout(layout);
  const cardById = useMemo(
    () => new Map(DASHBOARD_CARD_DEFS.map((card) => [card.id, card])),
    []
  );
  const standardPlacementById = getDashboardStandardPlacementById(normalizedLayout);

  return (
    <AnimatedModal onClose={onClose} size="lg">
      {({ close }) => (
        <>
          <div className="modal-header">
            <h3>Customize Dashboard</h3>
            <button type="button" className="modal-close" onClick={close} aria-label="Close">
              x
            </button>
          </div>
          <div className="dashboard-customize-modal">
            <DndContext
              sensors={sensors}
              collisionDetection={closestCenter}
              onDragStart={(event) => setDraggingId(event.active.id)}
              onDragCancel={() => setDraggingId(null)}
              onDragEnd={handleDragEnd}
            >
              <SortableContext
                items={normalizedLayout.map((item) => item.id)}
                strategy={verticalListSortingStrategy}
              >
                <div className="dashboard-customize-list">
                  {normalizedLayout.map((item) => {
                    const card = cardById.get(item.id);
                    if (!card) return null;
                    return (
                      <DashboardCustomizeRow
                        key={item.id}
                        id={item.id}
                        title={card.title}
                        description={card.description}
                        visible={item.visible}
                        wideSpan={item.wideSpan}
                        standardPlacement={standardPlacementById.get(item.id) || 'left'}
                        onToggle={() => toggleCard(item.id)}
                        onWideSpanChange={(wideSpan) => updateCardWideSpan(item.id, wideSpan)}
                      />
                    );
                  })}
                </div>
              </SortableContext>
            </DndContext>
          </div>
          <div className="modal-actions">
            <button type="button" className="btn-primary" onClick={close}>
              Done
            </button>
          </div>
        </>
      )}
    </AnimatedModal>
  );
}

function DashboardCustomizeRow({
  id,
  title,
  description,
  visible,
  wideSpan,
  standardPlacement,
  onToggle,
  onWideSpanChange
}) {
  const placementLabel = standardPlacement === 'right' ? 'right side' : 'left side';

  return (
    <ReorderListItem
      id={id}
      className="dashboard-customize-row"
      handleLabel={`Move ${title}`}
      title={title}
      subtitle={description}
      side={(
        <span className="dashboard-customize-controls">
          <span
            className="dashboard-column-picker"
            role="group"
            aria-label={`${title} width on wider screens`}
          >
            <button
              type="button"
              className={wideSpan === 1 ? 'active' : ''}
              aria-label={`Use standard width for ${title}, previewed on the ${placementLabel}`}
              aria-pressed={wideSpan === 1}
              title={`Standard Width (${placementLabel})`}
              onClick={() => onWideSpanChange(1)}
            >
              <span
                className={`dashboard-width-icon dashboard-width-icon-standard dashboard-width-icon-${standardPlacement}`}
                aria-hidden="true"
              />
            </button>
            <button
              type="button"
              className={wideSpan === 2 ? 'active' : ''}
              aria-label={`Use wide width for ${title}`}
              aria-pressed={wideSpan === 2}
              title="Wide Width"
              onClick={() => onWideSpanChange(2)}
            >
              <span
                className="dashboard-width-icon dashboard-width-icon-wide"
                aria-hidden="true"
              />
            </button>
          </span>
          <label className="toggle-switch dashboard-card-toggle">
            <input
              type="checkbox"
              aria-label={`Show ${title}`}
              checked={visible}
              onChange={onToggle}
            />
            <span className="toggle-slider" aria-hidden="true" />
          </label>
        </span>
      )}
    />
  );
}

function getDashboardStandardPlacementById(layout) {
  const placements = new Map();
  let filledColumns = 0;

  for (const item of layout) {
    const standardPlacement = filledColumns === 0 ? 'left' : 'right';
    placements.set(item.id, standardPlacement);

    if (!item.visible) continue;

    if (item.wideSpan === 2) {
      filledColumns = 0;
    } else {
      filledColumns = standardPlacement === 'left' ? 1 : 0;
    }
  }

  return placements;
}
