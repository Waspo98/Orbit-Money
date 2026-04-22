import { useSortable } from '@dnd-kit/sortable';
import { CSS } from '@dnd-kit/utilities';

export default function ReorderListItem({
  id,
  as: Element = 'div',
  className = '',
  handleLabel,
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
  } = useSortable({ id });

  const style = {
    transform: CSS.Transform.toString(transform),
    transition
  };

  return (
    <Element
      ref={setNodeRef}
      style={style}
      className={`selectable-list-item reorder-list-row draggable ${className} ${isDragging ? 'dragging' : ''}`}
    >
      <button
        type="button"
        ref={setActivatorNodeRef}
        className="drag-grip reorder-drag-handle"
        aria-label={handleLabel}
        {...attributes}
        {...listeners}
      >
        <span aria-hidden="true">⋮⋮</span>
      </button>
      <span className="selectable-list-leading">
        {leading}
      </span>
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
