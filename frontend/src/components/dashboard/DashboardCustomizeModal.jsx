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

  return (
    <AnimatedModal onClose={onClose} size="lg">
      {({ close }) => (
        <>
          <div className="modal-header">
            <h3>Customize My Dashboard</h3>
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
                        onToggle={() => toggleCard(item.id)}
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

function DashboardCustomizeRow({ id, title, description, visible, onToggle }) {
  return (
    <ReorderListItem
      id={id}
      className="dashboard-customize-row"
      handleLabel={`Move ${title}`}
      title={title}
      subtitle={description}
      side={(
        <label className="toggle-switch dashboard-card-toggle">
          <input
            type="checkbox"
            aria-label={`Show ${title}`}
            checked={visible}
            onChange={onToggle}
          />
          <span className="toggle-slider" aria-hidden="true" />
        </label>
      )}
    />
  );
}
