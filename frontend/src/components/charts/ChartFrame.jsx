export default function ChartFrame({
  className = '',
  width,
  height,
  label,
  children,
  ...props
}) {
  return (
    <svg
      className={className}
      viewBox={`0 0 ${width} ${height}`}
      role="img"
      aria-label={label}
      {...props}
    >
      {children}
    </svg>
  );
}
