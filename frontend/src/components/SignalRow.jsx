export default function SignalRow({
  label,
  value,
  detail,
  compactDetail = null,
  className = '',
  detailClassName = ''
}) {
  return (
    <div className={`signal-row networth-signal-row ${className}`.trim()}>
      <div>
        <span>{label}</span>
        <strong>{value}</strong>
      </div>
      {detail && (
        <em
          className={detailClassName}
          data-compact-detail={compactDetail ?? detail}
        >
          {detail}
        </em>
      )}
    </div>
  );
}
