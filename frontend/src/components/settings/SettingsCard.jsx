export default function SettingsCard({
  id,
  title,
  description,
  className = '',
  children
}) {
  return (
    <section
      className={`settings-section settings-card ${className}`.trim()}
      aria-labelledby={`settings-card-${id}`}
    >
      <div className="settings-card-header-static">
        <span className="settings-section-header">
          <h3 id={`settings-card-${id}`}>{title}</h3>
          {description && <p>{description}</p>}
        </span>
      </div>

      <div id={`settings-card-body-${id}`} className="settings-card-body">
        {children}
      </div>
    </section>
  );
}
