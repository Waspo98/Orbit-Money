export default function TapIndicatorText({ children, className = '', ...props }) {
  return (
    <p className={`tap-indicator-text ${className}`.trim()} {...props}>
      {children}
    </p>
  );
}
