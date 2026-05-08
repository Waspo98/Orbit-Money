export default function EmptyState({
  icon = null,
  title,
  description,
  children,
  action,
  className = '',
  as: Tag = 'div'
}) {
  const hasStructuredContent = icon != null || title || description || action;
  return (
    <Tag className={`empty-state ${className}`.trim()}>
      {hasStructuredContent ? (
        <>
          {icon != null && <div className="empty-state-icon">{icon}</div>}
          {title && <h2>{title}</h2>}
          {description && <p>{description}</p>}
          {action}
        </>
      ) : (
        children
      )}
    </Tag>
  );
}
