import { useEffect } from 'react';
import { MouseSensor, TouchSensor, useSensor, useSensors } from '@dnd-kit/core';
import { useSortable } from '@dnd-kit/sortable';
import { CSS } from '@dnd-kit/utilities';

export function useReorderSensors() {
  return useSensors(
    useSensor(MouseSensor, {
      activationConstraint: { distance: 8 }
    }),
    useSensor(TouchSensor, {
      activationConstraint: { delay: 180, tolerance: 8 }
    })
  );
}

export function useDragInteractionLock(isActive) {
  useEffect(() => {
    if (!isActive) return undefined;

    document.body.classList.add('drag-interaction-lock');
    return () => {
      document.body.classList.remove('drag-interaction-lock');
    };
  }, [isActive]);
}

function ReorderListItemPreview({
  as: Element = 'div',
  className = '',
  leading,
  title,
  subtitle,
  sidePrimary,
  sideSecondary,
  style,
  itemRef,
  handleRef,
  handleLabel,
  handleProps
}) {
  return (
    <Element
      ref={itemRef}
      style={style}
      className={`selectable-list-item reorder-list-row ${leading ? 'has-leading' : 'no-leading'} ${className}`.trim()}
    >
      <button
        type="button"
        ref={handleRef}
        className="drag-grip reorder-drag-handle"
        aria-label={handleLabel}
        {...handleProps}
      >
        <span aria-hidden="true">⋮⋮</span>
      </button>
      {leading && (
        <span className="selectable-list-leading">
          {leading}
        </span>
      )}
      <span className="selectable-list-main">
        <strong>{title}</strong>
        {subtitle && <em>{subtitle}</em>}
      </span>
      <span className="selectable-list-side">
        <strong>{sidePrimary}</strong>
        {sideSecondary && <em>{sideSecondary}</em>}
      </span>
    </Element>
  );
}

// Shared reorder row primitive for sortable lists. Keep drag activation on the
// handle so row content remains visually stable and does not become a drag slab.
export default function ReorderListItem({
  id,
  as = 'div',
  className = '',
  handleLabel,
  disabled = false,
  previewDisplaced = false,
  leading,
  title,
  subtitle,
  sidePrimary,
  sideSecondary
}) {
  const {
    attributes,
    listeners,
    setNodeRef,
    setActivatorNodeRef,
    transform,
    transition,
    isDragging
  } = useSortable({ id, disabled });

  const style = {
    transform: CSS.Transform.toString(transform),
    transition
  };

  return (
    <ReorderListItemPreview
      as={as}
      className={`draggable ${className} ${isDragging ? 'dragging' : ''} ${previewDisplaced ? 'drag-preview-target' : ''}`.trim()}
      leading={leading}
      title={title}
      subtitle={subtitle}
      sidePrimary={sidePrimary}
      sideSecondary={sideSecondary}
      style={style}
      itemRef={setNodeRef}
      handleRef={setActivatorNodeRef}
      handleLabel={handleLabel}
      handleProps={{ ...attributes, ...listeners }}
    />
  );
}
